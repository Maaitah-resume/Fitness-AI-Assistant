package com.fitnessai.chat.service;

import com.fitnessai.chat.dto.ChatDtos.FileRow;
import com.fitnessai.chat.dto.ChatDtos.HistoryData;
import com.fitnessai.chat.dto.ChatDtos.HistoryEvent;
import com.fitnessai.chat.dto.ChatDtos.MealRow;
import com.fitnessai.chat.dto.ChatDtos.NutritionData;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class UserContextServiceTest {

    @Test
    void contextIsRebuiltOnTheFirstMessageAndEveryFourthAfter() {
        assertThat(List.of(0L, 1L, 2L, 3L, 4L, 5L, 8L).stream().map(UserContextService::refreshDue))
                .containsExactly(true, false, false, false, true, false, true);
    }

    @Test
    void recentMealsHistoryAndFilesAreSpelledOut() {
        String today = LocalDate.now(ZoneOffset.UTC).toString();
        String old = LocalDate.now(ZoneOffset.UTC).minusDays(10).toString();
        assertThat(UserContextService.meals(new NutritionData(List.of(
                new MealRow(today, "Chicken and rice", 650, 45), new MealRow(old, "Old pizza", 900, 30)))))
                .contains("Chicken and rice, 650 kcal, 45 g protein").doesNotContain("Old pizza");

        assertThat(UserContextService.history(new HistoryData("2026-08-31", 58.5, 61.3, 2.8, List.of(
                new HistoryEvent("2026-10-01", "goal", "fat_loss", "muscle_gain"),
                new HistoryEvent("2026-10-02", "badge", null, "first_workout")))))
                .contains("since 2026-08-31", "58.5 kg -> 61.3 kg (+2.8 kg)", "goal changed from fat_loss to muscle_gain")
                .doesNotContain("badge");

        assertThat(UserContextService.files(List.of(
                new FileRow("Beginner plan.pdf", "generated", "ready", "2026-10-20T10:00:00Z"),
                new FileRow("ExerciseBook.pdf", "upload", "ready", null))))
                .contains("Beginner plan.pdf (created by you, the coach, 2026-10-20)", "ExerciseBook.pdf (uploaded by the user)");
    }
}
