package com.fitnessai.profile.repository;

import com.fitnessai.profile.model.Preferences;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PreferencesRepository extends JpaRepository<Preferences, Long> {
}
