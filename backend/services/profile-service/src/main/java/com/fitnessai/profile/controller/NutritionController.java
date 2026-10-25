package com.fitnessai.profile.controller;

import com.fitnessai.profile.dto.ApiResponse;
import com.fitnessai.profile.dto.NutritionDtos.DayTotal;
import com.fitnessai.profile.dto.NutritionDtos.MealRequest;
import com.fitnessai.profile.dto.NutritionDtos.MealView;
import com.fitnessai.profile.dto.NutritionDtos.NutritionView;
import com.fitnessai.profile.model.MealEntry;
import com.fitnessai.profile.repository.MealEntryRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

/** The caller's meal log (manual or photo-estimated). */
@RestController
@RequestMapping("/api/v1/profile/nutrition/me")
public class NutritionController {

    private static final int DAYS = 30;

    private final MealEntryRepository meals;

    public NutritionController(MealEntryRepository meals) {
        this.meals = meals;
    }

    @GetMapping
    public ApiResponse<NutritionView> view(@AuthenticationPrincipal Jwt jwt) {
        List<MealEntry> recent = meals.findByUserIdAndDateGreaterThanEqualOrderByDateDescIdDesc(userId(jwt),
                LocalDate.now(ZoneOffset.UTC).minusDays(DAYS - 1));
        Map<LocalDate, DayTotal> byDay = new TreeMap<>();
        for (MealEntry m : recent) {
            byDay.merge(m.getDate(), new DayTotal(m.getDate(), m.getCalories(), m.getProteinG(), 1),
                    (a, b) -> new DayTotal(a.date(), a.calories() + b.calories(), a.proteinG() + b.proteinG(), a.meals() + 1));
        }
        return ApiResponse.ok(new NutritionView(recent.stream().map(MealView::from).toList(), List.copyOf(byDay.values())));
    }

    @PostMapping
    public ApiResponse<MealView> log(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody MealRequest req) {
        LocalDate date = req.date() == null ? LocalDate.now(ZoneOffset.UTC) : req.date();
        return ApiResponse.ok(MealView.from(meals.save(new MealEntry(userId(jwt), date, req.name(), req.calories(),
                req.proteinG(), req.carbsG(), req.fatG(), req.sourceOrManual()))));
    }

    @DeleteMapping("/{id}")
    @Transactional
    public ApiResponse<Void> delete(@AuthenticationPrincipal Jwt jwt, @PathVariable long id) {
        long userId = userId(jwt);
        meals.delete(meals.findById(id).filter(m -> m.getUserId() == userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Meal not found.")));
        return ApiResponse.ok(null);
    }

    private static long userId(Jwt jwt) {
        return Long.parseLong(jwt.getSubject());
    }
}
