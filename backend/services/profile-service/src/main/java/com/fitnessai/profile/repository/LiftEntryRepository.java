package com.fitnessai.profile.repository;

import com.fitnessai.profile.model.LiftEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface LiftEntryRepository extends JpaRepository<LiftEntry, Long> {

    List<LiftEntry> findByUserIdOrderByDateAscIdAsc(Long userId);

    void deleteByUserId(Long userId);
}
