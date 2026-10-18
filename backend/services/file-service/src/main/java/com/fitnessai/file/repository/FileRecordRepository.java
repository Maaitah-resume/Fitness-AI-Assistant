package com.fitnessai.file.repository;

import com.fitnessai.file.model.FileRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface FileRecordRepository extends JpaRepository<FileRecord, Long> {

    List<FileRecord> findByOwnerIdOrderByCreatedAtDesc(Long ownerId);

    List<FileRecord> findAllByOrderByCreatedAtDesc();

    List<FileRecord> findByOwnerId(Long ownerId);

    @Query("select f.type, f.status, count(f), coalesce(sum(f.sizeBytes), 0) from FileRecord f group by f.type, f.status")
    List<Object[]> countsByTypeAndStatus();
}
