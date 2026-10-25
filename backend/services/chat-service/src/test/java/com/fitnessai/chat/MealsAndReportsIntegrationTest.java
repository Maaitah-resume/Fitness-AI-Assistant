package com.fitnessai.chat;

import com.fitnessai.chat.client.DownstreamClients;
import com.fitnessai.chat.dto.ChatDtos.CoachPreferences;
import com.fitnessai.chat.dto.ChatDtos.DocSection;
import com.fitnessai.chat.dto.ChatDtos.GenerateRequest;
import com.fitnessai.chat.dto.ChatDtos.GeneratedFile;
import com.fitnessai.chat.dto.ChatDtos.LiftRow;
import com.fitnessai.chat.dto.ChatDtos.MealRow;
import com.fitnessai.chat.dto.ChatDtos.NutritionData;
import com.fitnessai.chat.dto.ChatDtos.ProfileSnapshot;
import com.fitnessai.chat.dto.ChatDtos.ProgressData;
import com.fitnessai.chat.dto.ChatDtos.WeightRow;
import com.fitnessai.chat.memory.LongTermMemoryExtractor;
import com.fitnessai.chat.security.SecurityConfig;
import com.fitnessai.chat.service.AssistantModel;
import com.fitnessai.chat.service.MealVision;
import com.fitnessai.chat.service.MealVision.MealEstimate;
import com.fitnessai.chat.service.ReportWriter;
import com.fitnessai.chat.service.ReportWriter.ReportDraft;
import com.fitnessai.chat.service.WeeklyReportService;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reports;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "events.redis.enabled=false"
})
@AutoConfigureMockMvc
class MealsAndReportsIntegrationTest {

    private static final String R = "/api/v1/chat/reports/weekly";

    @Autowired MockMvc mvc;
    @Autowired WeeklyReportService service;

    @MockitoBean AssistantModel model;
    @MockitoBean DownstreamClients clients;
    @MockitoBean LongTermMemoryExtractor longTermMemory;
    @MockitoBean StringRedisTemplate redis;
    @MockitoBean MealVision vision;
    @MockitoBean ReportWriter writer;

    @Test
    void weeklyReportIsWrittenOncePerWeekAndFallsBackWithoutAi() throws Exception {
        LocalDate mon = service.dueWeek();
        // user 9 logged nothing last week
        when(clients.myProgressData("tok-9")).thenReturn(new ProgressData(List.of(), List.of()));
        mvc.perform(post(R).with(user(9))).andExpect(jsonPath("$.data.status").value("no_data"));

        // user 8: a weigh-in before and during the week, two squat sessions, meals
        when(clients.myProgressData("tok-8")).thenReturn(new ProgressData(
                List.of(new WeightRow(mon.minusDays(3).toString(), 60.0), new WeightRow(mon.plusDays(5).toString(), 60.8)),
                List.of(new LiftRow(mon.minusDays(4).toString(), "Back squat", 60, 5, 3, 70.0),
                        new LiftRow(mon.plusDays(1).toString(), "Back squat", 65, 5, 3, 75.8),
                        new LiftRow(mon.plusDays(4).toString(), "Back squat", 67.5, 5, null, 78.8))));
        when(clients.myNutrition("tok-8")).thenReturn(new NutritionData(List.of(
                new MealRow(mon.plusDays(1).toString(), "Oats", 500, 20), new MealRow(mon.plusDays(1).toString(), "Chicken", 700, 50))));
        when(clients.myProfile("tok-8")).thenReturn(new ProfileSnapshot(22, "other", 170.0, 60.0, 20.8, "muscle_gain", "beginner", true));
        when(clients.myPreferences("tok-8")).thenReturn(new CoachPreferences("metric", "short", "friendly", 3, null, null));
        when(clients.generateDocument(eq("tok-8"), any())).thenReturn(new GeneratedFile(41L, "Weekly report.pdf", "/api/v1/files/41/download"));
        when(writer.write(anyString(), anyBoolean())).thenReturn(new ReportDraft("Squat up 8.8 kg; one session short of plan.",
                List.of(new DocSection("Highlights", "- Squat PR"))));

        mvc.perform(post(R).with(user(8)))
                .andExpect(jsonPath("$.data.status").value("ready"))
                .andExpect(jsonPath("$.data.aiGenerated").value(true))
                .andExpect(jsonPath("$.data.fileId").value(41))
                .andExpect(jsonPath("$.data.weekStart").value(mon.toString()));
        ArgumentCaptor<String> digest = ArgumentCaptor.forClass(String.class);
        verify(writer).write(digest.capture(), eq(false));
        assertThat(digest.getValue()).contains("Training sessions: 2 of 3 planned",
                "best estimated 1RM 78.8 kg this week, previous best 70.0 kg (+8.8 kg)",
                "Body weight: 60.0 kg -> 60.8 kg (+0.8 kg)", "2 meals on 1 days, averaging 1200 kcal and 70 g protein",
                "protein target 97 g/day");
        ArgumentCaptor<GenerateRequest> pdf = ArgumentCaptor.forClass(GenerateRequest.class);
        verify(clients).generateDocument(eq("tok-8"), pdf.capture());
        assertThat(pdf.getValue().title()).startsWith("Weekly report: ");
        assertThat(pdf.getValue().sections().get(0).heading()).isEqualTo("Summary");

        // asking again the same week returns the stored report without writing a new one
        mvc.perform(post(R).with(user(8))).andExpect(jsonPath("$.data.fileId").value(41));
        verify(writer, times(1)).write(anyString(), anyBoolean());
        mvc.perform(get(R + "/latest").with(user(8)))
                .andExpect(jsonPath("$.data.report.summary").value("Squat up 8.8 kg; one session short of plan."))
                .andExpect(jsonPath("$.data.dueWeekStart").value(mon.toString()));

        // forced regeneration while Gemini is over quota: a data-only report, in Arabic
        when(writer.write(anyString(), anyBoolean())).thenThrow(new RuntimeException("429 quota"));
        when(clients.generateDocument(eq("tok-8"), any())).thenReturn(new GeneratedFile(42L, "Weekly report 2.pdf", "/api/v1/files/42/download"));
        mvc.perform(post(R).param("force", "true").param("language", "ar").with(user(8)))
                .andExpect(jsonPath("$.data.aiGenerated").value(false))
                .andExpect(jsonPath("$.data.fileId").value(42));
        verify(clients, times(2)).generateDocument(eq("tok-8"), pdf.capture());
        assertThat(pdf.getValue().title()).startsWith("التقرير الأسبوعي: ");
        assertThat(pdf.getValue().sections()).extracting(DocSection::heading).contains("التمرين", "وزن الجسم", "التغذية");
    }

    @Test
    void mealPhotoIsAnalysedAndErrorsAreExplained() throws Exception {
        String A = "/api/v1/chat/meals/analyze";
        mvc.perform(multipart(A).file(new MockMultipartFile("image", "notes.txt", "text/plain", "hi".getBytes())).with(user(5)))
                .andExpect(status().isBadRequest());

        when(vision.estimate(any(), any(), eq(false))).thenReturn(new MealEstimate(true, "Chicken and rice",
                List.of("grilled chicken, about 150 g", "white rice, about 1 cup"), 650, 45, 70, 12, "medium", "Typical portion assumed."));
        mvc.perform(multipart(A).file(new MockMultipartFile("image", "meal.jpg", "image/jpeg", new byte[]{1, 2, 3})).with(user(5)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.calories").value(650))
                .andExpect(jsonPath("$.data.items.length()").value(2));

        reset(vision);
        when(vision.estimate(any(), any(), anyBoolean())).thenThrow(new RuntimeException("429 You exceeded your current quota"));
        mvc.perform(multipart(A).file(new MockMultipartFile("image", "meal.png", "image/png", new byte[]{1})).with(user(5)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("usage limit")));
    }

    private static JwtRequestPostProcessor user(long id) {
        return jwt().jwt(j -> j.tokenValue("tok-" + id).subject(String.valueOf(id)).claim("role", "USER"))
                .authorities(SecurityConfig.roleAuthorities());
    }
}
