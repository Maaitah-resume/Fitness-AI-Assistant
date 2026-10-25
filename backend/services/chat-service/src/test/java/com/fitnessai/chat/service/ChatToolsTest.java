package com.fitnessai.chat.service;

import com.fitnessai.chat.client.DownstreamClients;
import com.fitnessai.chat.dto.ChatDtos.DocSection;
import com.fitnessai.chat.dto.ChatDtos.GenerateRequest;
import com.fitnessai.chat.dto.ChatDtos.GeneratedFile;
import com.fitnessai.chat.dto.ChatDtos.LiftLog;
import com.fitnessai.chat.dto.ChatDtos.ProfileSnapshot;
import com.fitnessai.chat.dto.ChatDtos.ProfileUpdate;
import com.fitnessai.chat.dto.ChatDtos.WeightLog;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;

import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ChatToolsTest {

    private final DownstreamClients clients = mock(DownstreamClients.class);
    private final ChatTools tools = new ChatTools("tok", clients);

    @Test
    void profileUpdateForwardsOnlyGivenFieldsWithCallerToken() {
        when(clients.updateProfile(eq("tok"), any())).thenReturn(
                new ProfileSnapshot(25, null, null, null, null, "muscle_gain", null, false));

        String result = tools.updateFitnessProfile(25, null, null, null, "muscle_gain", null);

        verify(clients).updateProfile("tok", new ProfileUpdate(25, null, null, null, "muscle_gain", null));
        assertThat(result).contains("age=25", "goal=muscle_gain", "still incomplete");
        assertThat(tools.profileUpdated()).isTrue();
    }

    @Test
    void rejectedProfileValuesAreReportedBackToTheModel() {
        when(clients.updateProfile(eq("tok"), any())).thenThrow(HttpClientErrorException.create(
                HttpStatus.BAD_REQUEST, "Bad Request", null,
                "{\"message\":\"age must be greater than or equal to 10\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8));

        String result = tools.updateFitnessProfile(5, null, null, null, null, null);

        assertThat(result).contains("rejected", "age must be greater than or equal to 10");
        assertThat(tools.profileUpdated()).isFalse();
    }

    @Test
    void generatedDocumentsAreCollectedForTheResponse() {
        List<DocSection> sections = List.of(new DocSection("Day 1", "- Squat 3x8"));
        when(clients.generateDocument("tok", new GenerateRequest("4-week plan", sections)))
                .thenReturn(new GeneratedFile(12L, "4-week-plan.pdf", "/api/v1/files/12/download"));

        String result = tools.generateDocument("4-week plan", sections);

        assertThat(result).contains("4-week-plan.pdf");
        assertThat(tools.generatedFiles()).singleElement()
                .extracting(GeneratedFile::downloadUrl).isEqualTo("/api/v1/files/12/download");
    }

    @Test
    void documentFailureLeavesNoPhantomFile() {
        when(clients.generateDocument(eq("tok"), any())).thenThrow(new RuntimeException("file-service down"));
        assertThat(tools.generateDocument("x", List.of())).contains("failed");
        assertThat(tools.generatedFiles()).isEmpty();
    }

    @Test
    void reportedWorkoutsAndWeighInsAreLoggedAsTheCaller() {
        String lift = tools.logLift("Back squat", 70.0, 5, 3, null);
        String weight = tools.logBodyWeight(61.5, "null");

        verify(clients).logLift("tok", new LiftLog(null, "Back squat", 70.0, 5, 3));
        verify(clients).logWeight("tok", new WeightLog(null, 61.5));
        assertThat(lift).contains("Back squat", "70.0 kg x5 for 3 sets");
        assertThat(weight).contains("61.5 kg");
        assertThat(tools.progressLogged()).isTrue();
        assertThat(tools.profileUpdated()).isTrue();
    }

    @Test
    void aPersonalRecordIsCalledOutSoTheCoachCelebrates() {
        when(clients.logLift(eq("tok"), any())).thenReturn(true);
        assertThat(tools.logLift("Deadlift", 100.0, 3, null, null)).contains("NEW PERSONAL RECORD for Deadlift");
    }

    @Test
    void rejectedLiftIsReportedBackToTheModel() {
        doThrow(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null,
                "{\"message\":\"reps must be greater than or equal to 1\"}".getBytes(StandardCharsets.UTF_8),
                StandardCharsets.UTF_8)).when(clients).logLift(eq("tok"), any());

        String result = tools.logLift("Bench press", 60.0, 0, null, "2026-10-20");

        assertThat(result).contains("rejected", "reps must be greater than or equal to 1");
        assertThat(tools.progressLogged()).isFalse();
    }
}
