package com.fitnessai.profile.repository;

import com.fitnessai.profile.model.BadgeDefinition;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface BadgeDefinitionRepository extends JpaRepository<BadgeDefinition, Long> {

    List<BadgeDefinition> findAllByOrderBySortOrderAscIdAsc();

    List<BadgeDefinition> findByActiveTrueOrderBySortOrderAscIdAsc();

    boolean existsByCode(String code);
}
