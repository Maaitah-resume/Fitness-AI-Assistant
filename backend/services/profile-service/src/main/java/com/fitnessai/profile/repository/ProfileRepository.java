package com.fitnessai.profile.repository;

import com.fitnessai.profile.model.Profile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;

public interface ProfileRepository extends JpaRepository<Profile, Long> {

    @Query("select p.goal, count(p) from Profile p where p.goal is not null group by p.goal")
    List<Object[]> countByGoal();

    @Query("select p.fitnessLevel, count(p) from Profile p where p.fitnessLevel is not null group by p.fitnessLevel")
    List<Object[]> countByLevel();

    @Query("select p.gender, count(p) from Profile p where p.gender is not null group by p.gender")
    List<Object[]> countByGender();

    @Query("select avg(p.age) from Profile p")
    Double averageAge();
}
