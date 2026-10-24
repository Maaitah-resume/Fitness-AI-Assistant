package com.fitnessai.file;

import com.fitnessai.file.client.KnowledgeClient;
import com.fitnessai.file.pdf.PdfRenderer;
import com.fitnessai.file.pdf.PdfTextExtractor;
import com.fitnessai.file.security.SecurityConfig;
import com.fitnessai.file.storage.MinioObjectStorage;
import com.fitnessai.file.storage.ObjectStorage;
import com.jayway.jsonpath.JsonPath;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:files;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "events.redis.enabled=false"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class FileServiceIntegrationTest {

    @TestConfiguration
    static class InMemoryStorageConfig {
        @Bean
        @Primary
        ObjectStorage inMemoryStorage() {
            Map<String, byte[]> objects = new ConcurrentHashMap<>();
            return new ObjectStorage() {
                public void put(String key, byte[] content, String contentType) { objects.put(key, content); }
                public byte[] get(String key) { return objects.get(key); }
                public void delete(String key) { objects.remove(key); }
            };
        }
    }

    @Autowired MockMvc mvc;
    @Autowired PdfRenderer renderer;
    @Autowired PdfTextExtractor extractor;
    @Autowired MinioObjectStorage realMinioClientStillConstructs;
    @MockitoBean KnowledgeClient knowledge;

    @Test
    void uploadIndexDownloadDelete() throws Exception {
        byte[] pdf = renderer.render("Upper Lower Split", List.of(
                new PdfRenderer.Section("Day 1 - Upper", "- Bench press 4x6\n- Barbell row 4x8"),
                new PdfRenderer.Section("Day 2 - Lower", "Squats and Romanian deadlifts.")));

        String body = mvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "My Plan (v2)!.pdf", "application/pdf", pdf))
                        .with(user(7, "USER", "tok-7")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("indexed"))
                .andExpect(jsonPath("$.data.filename").value("My Plan v2.pdf"))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();

        // text was really extracted from the PDF and sent for indexing as the uploader
        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<String>> chunks = ArgumentCaptor.forClass(List.class);
        verify(knowledge).index(eq("tok-7"), eq("file-" + id), eq("My Plan v2.pdf"), eq(false), chunks.capture());
        assertThat(String.join(" ", chunks.getValue())).contains("Bench press 4x6", "Romanian deadlifts");

        // access control on download
        mvc.perform(get("/api/v1/files/" + id + "/download").with(user(7, "USER", "tok-7")))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("My Plan v2.pdf")));
        mvc.perform(get("/api/v1/files/" + id + "/download").with(user(8, "USER", "tok-8")))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/files/" + id + "/download").with(user(1, "ADMIN", "tok-1")))
                .andExpect(status().isOk());

        // listing is per-owner
        mvc.perform(get("/api/v1/files").with(user(7, "USER", "tok-7")))
                .andExpect(jsonPath("$.data.files.length()").value(1));
        mvc.perform(get("/api/v1/files").with(user(8, "USER", "tok-8")))
                .andExpect(jsonPath("$.data.files.length()").value(0));

        // if the index can't be cleaned, nothing is deleted
        doThrow(new RuntimeException("knowledge down")).when(knowledge).delete("tok-7", "file-" + id);
        mvc.perform(delete("/api/v1/files/" + id).with(user(7, "USER", "tok-7")))
                .andExpect(status().isBadGateway());
        mvc.perform(get("/api/v1/files").with(user(7, "USER", "tok-7")))
                .andExpect(jsonPath("$.data.files.length()").value(1));

        // other users can't delete; owner can once the index is reachable
        mvc.perform(delete("/api/v1/files/" + id).with(user(8, "USER", "tok-8"))).andExpect(status().isNotFound());
        org.mockito.Mockito.reset(knowledge);
        mvc.perform(delete("/api/v1/files/" + id).with(user(7, "USER", "tok-7"))).andExpect(status().isOk());
        verify(knowledge).delete("tok-7", "file-" + id);
        mvc.perform(get("/api/v1/files").with(user(7, "USER", "tok-7")))
                .andExpect(jsonPath("$.data.files.length()").value(0));
    }

    @Test
    void uploadValidationAndFailureStates() throws Exception {
        mvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "notes.pdf", "application/pdf", "just text".getBytes()))
                        .with(user(7, "USER", "t")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only PDF files are supported."));

        byte[] pdf = renderer.render("Shared guide", List.of(new PdfRenderer.Section("Intro", "Hello")));
        mvc.perform(multipart("/api/v1/files/upload").param("shared", "true")
                        .file(new MockMultipartFile("file", "guide.pdf", "application/pdf", pdf))
                        .with(user(7, "USER", "t")))
                .andExpect(status().isForbidden());

        // indexing failure keeps the file with a visible reason
        doThrow(new RuntimeException("timeout")).when(knowledge)
                .index(anyString(), anyString(), anyString(), anyBoolean(), anyList());
        mvc.perform(multipart("/api/v1/files/upload")
                        .file(new MockMultipartFile("file", "guide.pdf", "application/pdf", pdf))
                        .with(user(7, "USER", "t")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("failed"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("indexing failed")));
    }

    @Test
    void sharedAdminUploadsAreReadableByEveryone() throws Exception {
        byte[] pdf = renderer.render("Exercise Book", List.of(new PdfRenderer.Section("Squat", "Keep your back neutral.")));
        String body = mvc.perform(multipart("/api/v1/files/upload").param("shared", "true")
                        .file(new MockMultipartFile("file", "ExerciseBook.pdf", "application/pdf", pdf))
                        .with(user(1, "ADMIN", "tok-1")))
                .andExpect(jsonPath("$.data.shared").value(true))
                .andReturn().getResponse().getContentAsString();
        long id = ((Number) JsonPath.read(body, "$.data.id")).longValue();
        verify(knowledge).index(eq("tok-1"), eq("file-" + id), anyString(), eq(true), anyList());

        mvc.perform(get("/api/v1/files/" + id + "/download").with(user(42, "USER", "t"))).andExpect(status().isOk());
    }

    @Test
    void generatedDocumentRoundTrip() throws Exception {
        String body = mvc.perform(post("/api/v1/files/generate").with(user(7, "USER", "t"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Your 4-Week Fat Loss Plan","sections":[
                                  {"heading":"Week 1","body":"- 3x full body\\n- 8k steps daily"},
                                  {"heading":"Nutrition","body":"Aim for a 300 kcal deficit — high protein."}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.type").value("generated"))
                .andExpect(jsonPath("$.data.status").value("indexed"))
                .andExpect(jsonPath("$.data.filename").value("Your 4-Week Fat Loss Plan.pdf"))
                .andReturn().getResponse().getContentAsString();
        String url = JsonPath.read(body, "$.data.downloadUrl");

        // indexed as a private document so later chats can retrieve it
        ArgumentCaptor<List<String>> chunks = ArgumentCaptor.forClass(List.class);
        verify(knowledge).index(eq("t"), anyString(), eq("Your 4-Week Fat Loss Plan.pdf"), eq(false), chunks.capture());
        assertThat(String.join(" ", chunks.getValue())).contains("8k steps daily", "300 kcal deficit");

        byte[] downloaded = mvc.perform(get(url).with(user(7, "USER", "t")))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsByteArray();
        assertThat(PdfTextExtractor.looksLikePdf(downloaded)).isTrue();
        assertThat(extractor.extract(downloaded))
                .contains("Your 4-Week Fat Loss Plan", "8k steps daily", "300 kcal deficit — high protein");

        // stats are admin-only
        mvc.perform(get("/api/v1/files/admin/stats").with(user(7, "USER", "t"))).andExpect(status().isForbidden());
        mvc.perform(get("/api/v1/files/admin/stats").with(user(1, "ADMIN", "t")))
                .andExpect(jsonPath("$.data.totalFiles").value(1))
                .andExpect(jsonPath("$.data.byType.generated").value(1));
    }

    @Test
    void generatedDocumentStaysDownloadableWhenIndexingFails() throws Exception {
        doThrow(new RuntimeException("knowledge down")).when(knowledge)
                .index(anyString(), anyString(), anyString(), anyBoolean(), anyList());
        mvc.perform(post("/api/v1/files/generate").with(user(7, "USER", "t"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"Plan","sections":[{"heading":"Day 1","body":"Squats 3x8"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ready"));

        // once knowledge-service is back, the backfill indexes it from the stored PDF; a second run is a no-op
        org.mockito.Mockito.reset(knowledge);
        mvc.perform(post("/api/v1/files/generated/index-pending").with(user(7, "USER", "t")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.indexed").value(1));
        ArgumentCaptor<List<String>> chunks = ArgumentCaptor.forClass(List.class);
        verify(knowledge).index(eq("t"), anyString(), eq("Plan.pdf"), eq(false), chunks.capture());
        assertThat(String.join(" ", chunks.getValue())).contains("Squats 3x8");
        mvc.perform(post("/api/v1/files/generated/index-pending").with(user(7, "USER", "t")))
                .andExpect(jsonPath("$.data.indexed").value(0));
        mvc.perform(get("/api/v1/files").with(user(7, "USER", "t")))
                .andExpect(jsonPath("$.data.files[0].status").value("indexed"));
    }

    @Test
    void arabicDocumentGeneratedFromChatKeepsItsTitleAndText() throws Exception {
        String body = mvc.perform(post("/api/v1/files/generate").with(user(7, "USER", "t"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .characterEncoding("UTF-8")
                        .content("""
                                {"title":"خطة خسارة الوزن","sections":[
                                  {"heading":"الأسبوع الأول","body":"- مشي 30 دقيقة يومياً\\n- تمرين القرفصاء 3x12"}]}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.filename").value("خطة خسارة الوزن.pdf"))
                .andReturn().getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8);
        String url = JsonPath.read(body, "$.data.downloadUrl");

        var response = mvc.perform(get(url).with(user(7, "USER", "t")))
                .andExpect(status().isOk())
                // non-ASCII names use the RFC 5987 form browsers decode
                .andExpect(header().string("Content-Disposition", org.hamcrest.Matchers.containsString("filename*=UTF-8''")))
                .andReturn().getResponse();
        assertThat(extractor.extract(response.getContentAsByteArray()))
                .contains("خطة خسارة الوزن", "مشي 30 دقيقة يومياً", "3x12 تمرين القرفصاء");
    }

    private static JwtRequestPostProcessor user(long id, String role, String token) {
        return jwt().jwt(j -> j.tokenValue(token).subject(String.valueOf(id)).claim("role", role))
                .authorities(SecurityConfig.roleAuthorities());
    }
}
