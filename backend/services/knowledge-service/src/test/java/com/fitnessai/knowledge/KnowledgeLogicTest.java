package com.fitnessai.knowledge;

import com.fitnessai.knowledge.dto.KnowledgeDtos.IndexRequest;
import com.fitnessai.knowledge.dto.KnowledgeDtos.RetrieveResponse;
import com.fitnessai.knowledge.graph.GraphService;
import com.fitnessai.knowledge.graph.Triple;
import com.fitnessai.knowledge.security.Caller;
import com.fitnessai.knowledge.service.KnowledgeService;
import com.fitnessai.knowledge.vector.VectorIndexService;
import org.junit.jupiter.api.Test;
import org.springframework.ai.document.Document;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class KnowledgeLogicTest {

    private final VectorIndexService vectors = mock(VectorIndexService.class);
    private final GraphService graph = mock(GraphService.class);
    private final KnowledgeService service = new KnowledgeService(vectors, graph);

    private final Caller user = new Caller(7, false);
    private final Caller admin = new Caller(1, true);

    @Test
    void indexingScopesPrivateByDefaultAndSharedOnlyForAdmins() {
        when(vectors.indexDocument(anyString(), anyString(), anyString(), anyList())).thenReturn(2);

        var result = service.index(user, new IndexRequest("doc-1", "plan.pdf", false, List.of("a", "b")));
        assertThat(result.scope()).isEqualTo("user:7");
        verify(graph).indexDocumentAsync("user:7", "doc-1", List.of("a", "b"));

        assertThatThrownBy(() -> service.index(user, new IndexRequest("doc-2", "x.pdf", true, List.of("a"))))
                .isInstanceOf(ResponseStatusException.class);

        assertThat(service.index(admin, new IndexRequest("doc-3", "book.pdf", true, List.of("a"))).scope())
                .isEqualTo("shared");
    }

    @Test
    void deletionIsScopedForUsersAndGlobalForAdmins() {
        service.deleteDocument(user, "doc-1");
        verify(vectors).deleteDocument("doc-1", "user:7");
        verify(graph).removeDocument("user:7", "doc-1");
        verify(graph, never()).removeDocumentEverywhere(anyString());

        service.deleteDocument(admin, "doc-9");
        verify(vectors).deleteDocument("doc-9", null);
        verify(graph).removeDocumentEverywhere("doc-9");
    }

    @Test
    void retrievalSearchesOnlyReadableScopesAndBuildsContext() {
        Document chunk = Document.builder().text("Squats build the quads.")
                .metadata(Map.of("filename", "book.pdf", "documentId", "doc-3")).score(0.8).build();
        when(vectors.searchDocuments(eq("squats?"), eq(List.of("shared", "user:7")), eq(4))).thenReturn(List.of(chunk));
        when(vectors.searchMemories(eq("squats?"), eq("user:7"), anyInt(), anyDouble()))
                .thenReturn(List.of(new Document("Has a knee injury")));
        when(graph.factsFor("squats?", List.of("shared", "user:7"))).thenReturn(List.of("Squat TARGETS Quadriceps"));

        RetrieveResponse r = service.retrieve(user, "squats?", null);

        assertThat(r.chunks()).singleElement().satisfies(c -> assertThat(c.filename()).isEqualTo("book.pdf"));
        assertThat(r.context())
                .contains("[Document excerpts]", "(1) book.pdf: Squats build the quads.")
                .contains("[Knowledge graph facts]", "- Squat TARGETS Quadriceps")
                .contains("[What we know about this user]", "- Has a knee injury");
    }

    @Test
    void retrievalDegradesWhenVectorSearchFails() {
        when(vectors.searchDocuments(anyString(), anyList(), anyInt())).thenThrow(new RuntimeException("401 from OpenAI"));
        when(vectors.searchMemories(anyString(), anyString(), anyInt(), anyDouble())).thenThrow(new RuntimeException("down"));
        when(graph.factsFor(anyString(), any())).thenReturn(List.of("Plank TARGETS Core"));

        RetrieveResponse r = service.retrieve(user, "plank", 3);

        assertThat(r.chunks()).isEmpty();
        assertThat(r.context()).isEqualTo("[Knowledge graph facts]\n- Plank TARGETS Core");
    }

    @Test
    void memoryResultCountsDuplicates() {
        when(vectors.addMemories("user:7", List.of("a", "b", "c"))).thenReturn(1);
        var r = service.remember(user, List.of("a", "b", "c"));
        assertThat(r.stored()).isEqualTo(1);
        assertThat(r.skippedAsDuplicate()).isEqualTo(2);
    }

    @Test
    void triplesAreClampedToKnownVocabulary() {
        Triple t = new Triple("Barbell Squat", "exercise", "works out", "Glutes", "body part");
        assertThat(t.isValid()).isTrue();
        assertThat(t.normalizedRelation()).isEqualTo("RELATED_TO");
        assertThat(Triple.normalizedType(t.subjectType())).isEqualTo("Exercise");
        assertThat(Triple.normalizedType(t.objectType())).isEqualTo("Other");
        assertThat(new Triple("Squat", "Exercise", "targets", "Quads", "MuscleGroup").normalizedRelation())
                .isEqualTo("TARGETS");

        assertThat(new Triple("Squat", null, "TARGETS", "squat!", null).isValid()).isFalse(); // self-loop
        assertThat(new Triple(null, null, "TARGETS", "Quads", null).isValid()).isFalse();
    }
}
