package com.fitnessai.chat.repository;

import com.fitnessai.chat.model.UserMemory;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface UserMemoryRepository extends JpaRepository<UserMemory, Long> {

    List<UserMemory> findByUserIdOrderByCreatedAtAsc(Long userId);

    @Transactional
    void deleteByUserId(Long userId);
}
