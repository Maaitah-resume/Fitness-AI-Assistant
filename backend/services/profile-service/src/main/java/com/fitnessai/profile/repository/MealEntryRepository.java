package com.fitnessai.profile.repository;

import com.fitnessai.profile.model.MealEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface MealEntryRepository extends JpaRepository<MealEntry, Long> {

    List<MealEntry> findByUserIdAndDateGreaterThanEqualOrderByDateDescIdDesc(Long userId, LocalDate since);

    void deleteByUserId(Long userId);
}
