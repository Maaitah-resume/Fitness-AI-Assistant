package com.fitnessai.profile.repository;

import com.fitnessai.profile.model.ProfileChange;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ProfileChangeRepository extends JpaRepository<ProfileChange, Long> {

    List<ProfileChange> findByUserIdOrderByChangedAtAsc(Long userId);

    void deleteByUserId(Long userId);
}
