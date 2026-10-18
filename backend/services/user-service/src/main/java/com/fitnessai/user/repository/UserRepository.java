package com.fitnessai.user.repository;

import com.fitnessai.user.model.Role;
import com.fitnessai.user.model.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    boolean existsByUsernameOrEmail(String username, String email);

    long countByRole(Role role);

    @Query("select u.createdAt from User u where u.createdAt >= :since")
    List<Instant> findSignupTimesSince(Instant since);
}
