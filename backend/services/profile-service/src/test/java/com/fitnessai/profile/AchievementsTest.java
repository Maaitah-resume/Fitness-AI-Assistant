package com.fitnessai.profile;

import com.fitnessai.profile.model.LiftEntry;
import com.fitnessai.profile.security.SecurityConfig;
import com.fitnessai.profile.service.AchievementsService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.TreeSet;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:achievements;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "events.redis.enabled=false"
})
@AutoConfigureMockMvc
class AchievementsTest {

    @Autowired MockMvc mvc;

    @Test
    void personalRecordsAreTheSetsThatBeatEveryEarlierOne() {
        LocalDate d = LocalDate.of(2026, 10, 1);
        List<LiftEntry> lifts = List.of(
                lift(1, d, "Back squat", 60, 5),            // baseline, not a PR
                lift(2, d.plusDays(2), "Back squat", 62.5, 5), // PR
                lift(3, d.plusDays(4), "back  squat", 62.5, 5), // ties: not a PR
                lift(4, d.plusDays(4), "Bench press", 40, 5),  // baseline
                lift(5, d.plusDays(6), "Back squat", 70, 1),   // heavier single but lower e1RM: not a PR
                lift(6, d.plusDays(8), "Back squat", 65, 5));  // PR
        assertThat(AchievementsService.personalRecordIds(lifts)).containsExactlyInAnyOrder(2L, 6L);
    }

    @Test
    void streaksBadgesAndPrFlagsFromTheLog() throws Exception {
        LocalDate monday = LocalDate.now(ZoneOffset.UTC).with(DayOfWeek.MONDAY);
        mvc.perform(put("/api/v1/profile/me").with(user(41)).contentType(MediaType.APPLICATION_JSON).content("{\"weightKg\":60}"));
        mvc.perform(put("/api/v1/profile/preferences/me").with(user(41)).contentType(MediaType.APPLICATION_JSON).content("{\"trainingDays\":2}"));

        // four previous weeks with 2 sessions each; squat climbs every session
        double kg = 50;
        for (int w = 4; w >= 1; w--) {
            for (int day : new int[]{0, 3}) {
                LocalDate date = monday.minusWeeks(w).plusDays(day);
                mvc.perform(post("/api/v1/profile/progress/me/lifts").with(user(41)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"" + date + "\",\"exercise\":\"Back squat\",\"weightKg\":" + kg + ",\"reps\":5}"));
                kg += 2.5;
            }
        }
        // logging a heavier set today is flagged as a record; a lighter one is not
        mvc.perform(post("/api/v1/profile/progress/me/lifts").with(user(41)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exercise\":\"Back squat\",\"weightKg\":70,\"reps\":5}"))
                .andExpect(jsonPath("$.data.personalRecord").value(true));
        mvc.perform(post("/api/v1/profile/progress/me/lifts").with(user(41)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exercise\":\"Back squat\",\"weightKg\":40,\"reps\":5}"))
                .andExpect(jsonPath("$.data.personalRecord").value(false));
        mvc.perform(get("/api/v1/profile/progress/me").with(user(41)))
                .andExpect(jsonPath("$.data.lifts[0].personalRecord").value(false))
                .andExpect(jsonPath("$.data.lifts[1].personalRecord").value(true));

        // meals on the last three days
        for (int back = 0; back < 3; back++) {
            mvc.perform(post("/api/v1/profile/nutrition/me").with(user(41)).contentType(MediaType.APPLICATION_JSON)
                    .content("{\"date\":\"" + LocalDate.now(ZoneOffset.UTC).minusDays(back) + "\",\"name\":\"Meal\",\"calories\":600,\"proteinG\":100}"));
        }

        mvc.perform(get("/api/v1/profile/achievements/me").with(user(41)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.totalWorkouts").value(9))
                .andExpect(jsonPath("$.data.trainingWeeks.target").value(2))
                .andExpect(jsonPath("$.data.trainingWeeks.current").value(4)) // this week (1 session so far) doesn't break it
                .andExpect(jsonPath("$.data.trainingWeeks.best").value(4))
                .andExpect(jsonPath("$.data.mealDays.current").value(3))
                .andExpect(jsonPath("$.data.totalPrs").value(8))
                .andExpect(jsonPath("$.data.recentPrs[0].weightKg").value(70.0))
                .andExpect(jsonPath("$.data.recentPrs[0].previousBest").value(78.8))
                .andExpect(jsonPath("$.data.badges[?(@.id=='first_workout')].earned").value(true))
                .andExpect(jsonPath("$.data.badges[?(@.id=='streak_4')].earned").value(true))
                .andExpect(jsonPath("$.data.badges[?(@.id=='prs_5')].earned").value(true))
                .andExpect(jsonPath("$.data.badges[?(@.id=='bodyweight_squat')].earned").value(true))
                .andExpect(jsonPath("$.data.badges[?(@.id=='workouts_10')].current").value(9.0))
                .andExpect(jsonPath("$.data.badges[?(@.id=='deadlift_100')].earned").value(false))
                .andExpect(jsonPath("$.data.badges[?(@.id=='protein_5')].current").value(3.0));
    }

    @Test
    void anUnfinishedWeekDoesNotBreakTheStreakButAMissedOneDoes() {
        LocalDate today = LocalDate.of(2026, 10, 28); // a Wednesday
        LocalDate mon = today.with(DayOfWeek.MONDAY);
        TreeSet<LocalDate> sessions = new TreeSet<>(List.of(
                mon.minusWeeks(4), mon.minusWeeks(4).plusDays(2),     // met
                mon.minusWeeks(3),                                     // missed (1 of 2)
                mon.minusWeeks(2), mon.minusWeeks(2).plusDays(2),     // met
                mon.minusWeeks(1), mon.minusWeeks(1).plusDays(2)));   // met; this week has none yet
        var streak = invoke(sessions, 2, today);
        assertThat(streak.current()).isEqualTo(2);
        assertThat(streak.best()).isEqualTo(2);
    }

    private static AchievementsService.Streak invoke(TreeSet<LocalDate> sessions, int target, LocalDate today) {
        return (AchievementsService.Streak) ReflectionTestUtils.invokeMethod(AchievementsService.class, "trainingStreak", sessions, target, today);
    }

    private static LiftEntry lift(long id, LocalDate date, String exercise, double kg, int reps) {
        LiftEntry e = new LiftEntry(1L, date, exercise, kg, reps, 3);
        ReflectionTestUtils.setField(e, "id", id);
        return e;
    }

    private static JwtRequestPostProcessor user(long id) {
        return jwt().jwt(j -> j.subject(String.valueOf(id)).claim("role", "USER")).authorities(SecurityConfig.roleAuthorities());
    }
}
