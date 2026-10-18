package com.fitnessai.user.dto;

import com.fitnessai.user.model.Role;
import com.fitnessai.user.model.User;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.Instant;
import java.util.Map;

public final class AuthDtos {

    private AuthDtos() {
    }

    public record RegisterRequest(
            @NotBlank @Size(min = 3, max = 50) String username,
            @NotBlank @Size(min = 8, max = 128) String password,
            @NotBlank @Email String email,
            String name) {
    }

    public record LoginRequest(@NotBlank String username, @NotBlank String password) {
    }

    // Field names match what frontend/src/api.js storeSession() reads.
    public record AuthResponse(
            String access_token,
            String token_type,
            long expires_in,
            Long user_id,
            String username,
            String email,
            String role) {
    }

    public record UserResponse(
            Long id,
            String username,
            String email,
            String name,
            String role,
            Instant created_at) {

        public static UserResponse from(User u) {
            return new UserResponse(u.getId(), u.getUsername(), u.getEmail(), u.getName(),
                    u.getRole().name().toLowerCase(), u.getCreatedAt());
        }
    }

    public record RoleUpdateRequest(@NotNull Role role) {
    }

    public record UserStats(
            long totalUsers,
            Map<String, Long> byRole,
            Map<String, Long> signupsByDay) {
    }
}
