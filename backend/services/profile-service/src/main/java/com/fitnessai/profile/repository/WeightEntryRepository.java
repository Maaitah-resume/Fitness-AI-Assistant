package com.fitnessai.profile.repository;

import com.fitnessai.profile.model.WeightEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface WeightEntryRepository extends JpaRepository<WeightEntry, Long> {

    List<WeightEntry> findByUserIdOrderByDateAsc(Long userId);

    Optional<WeightEntry> findByUserIdAndDate(Long userId, LocalDate date);

    Optional<WeightEntry> findFirstByUserIdOrderByDateDesc(Long userId);

    void deleteByUserId(Long userId);
}
