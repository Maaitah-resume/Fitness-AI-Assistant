package com.fitnessai.chat.repository;

import com.fitnessai.chat.model.WeeklyReport;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Optional;

public interface WeeklyReportRepository extends JpaRepository<WeeklyReport, Long> {

    Optional<WeeklyReport> findByUserIdAndWeekStart(Long userId, LocalDate weekStart);

    Optional<WeeklyReport> findFirstByUserIdOrderByWeekStartDesc(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
