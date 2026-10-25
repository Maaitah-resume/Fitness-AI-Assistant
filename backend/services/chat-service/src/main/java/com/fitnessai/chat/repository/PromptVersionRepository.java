package com.fitnessai.chat.repository;

import com.fitnessai.chat.model.PromptVersion;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PromptVersionRepository extends JpaRepository<PromptVersion, Long> {

    Optional<PromptVersion> findFirstByOrderByIdDesc();

    List<PromptVersion> findTop30ByOrderByIdDesc();
}
