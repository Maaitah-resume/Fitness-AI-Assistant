package com.fitnessai.user.service;

import com.fitnessai.user.dto.AuthDtos.AuthResponse;
import com.fitnessai.user.dto.AuthDtos.DeleteAccountRequest;
import com.fitnessai.user.dto.AuthDtos.PasswordChangeRequest;
import com.fitnessai.user.dto.AuthDtos.LoginRequest;
import com.fitnessai.user.dto.AuthDtos.RegisterRequest;
import com.fitnessai.user.dto.AuthDtos.UserResponse;
import com.fitnessai.user.dto.AuthDtos.UserStats;
import com.fitnessai.user.exception.ApiException;
import com.fitnessai.user.model.Role;
import com.fitnessai.user.model.User;
import com.fitnessai.user.repository.UserRepository;
import com.fitnessai.user.security.JwtTokenService;
import com.fitnessai.user.security.TokenRevocationStore;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
public class UserService {

    private final UserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenService tokens;
    private final TokenRevocationStore revocations;
    private final UserEventPublisher events;

    public UserService(UserRepository users, PasswordEncoder passwordEncoder, JwtTokenService tokens,
                       TokenRevocationStore revocations, UserEventPublisher events) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
        this.revocations = revocations;
        this.events = events;
    }

    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (users.existsByUsernameOrEmail(req.username(), req.email())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Username or email already in use.");
        }
        User user;
        try {
            user = users.saveAndFlush(new User(req.username(), req.email(), req.name(),
                    passwordEncoder.encode(req.password())));
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent registration for the same name/email.
            throw new ApiException(HttpStatus.BAD_REQUEST, "Username or email already in use.");
        }
        return authResponse(user);
    }

    public AuthResponse login(LoginRequest req) {
        User user = users.findByUsername(req.username())
                .filter(u -> passwordEncoder.matches(req.password(), u.getPasswordHash()))
                .orElseThrow(() -> new ApiException(HttpStatus.UNAUTHORIZED, "Invalid username or password."));
        return authResponse(user);
    }

    public void logout(Jwt jwt) {
        revocations.revoke(jwt.getId(), jwt.getExpiresAt());
    }

    @Transactional
    public void changePassword(long userId, PasswordChangeRequest req) {
        User user = find(userId);
        if (!passwordEncoder.matches(req.currentPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Current password is incorrect.");
        }
        if (passwordEncoder.matches(req.newPassword(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "The new password must be different from the current one.");
        }
        user.setPasswordHash(passwordEncoder.encode(req.newPassword()));
    }

    /**
     * A user deleting their own account: re-check the password, refuse to remove the last admin,
     * then delete, revoke this token and announce it so every service drops the user's data.
     */
    @Transactional
    public void deleteSelf(Jwt jwt, DeleteAccountRequest req) {
        long userId = Long.parseLong(jwt.getSubject());
        User user = find(userId);
        if (!passwordEncoder.matches(req.password(), user.getPasswordHash())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Password is incorrect.");
        }
        if (user.getRole() == Role.ADMIN && users.countByRole(Role.ADMIN) <= 1) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "You are the only admin. Make another user an admin before deleting your account.");
        }
        users.delete(user);
        revocations.revoke(jwt.getId(), jwt.getExpiresAt());
        events.userDeleted(userId);
    }

    public UserResponse me(long userId) {
        return UserResponse.from(find(userId));
    }

    public List<UserResponse> listAll() {
        return users.findAll(Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(UserResponse::from)
                .toList();
    }

    @Transactional
    public UserResponse updateRole(long targetId, Role role, long actingAdminId) {
        if (targetId == actingAdminId && role != Role.ADMIN) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot remove your own admin role.");
        }
        User user = find(targetId);
        user.setRole(role);
        return UserResponse.from(user);
    }

    @Transactional
    public void resetPassword(long targetId, String newPassword) {
        find(targetId).setPasswordHash(passwordEncoder.encode(newPassword));
    }

    @Transactional
    public void delete(long targetId, long actingAdminId) {
        if (targetId == actingAdminId) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "You cannot delete your own account.");
        }
        users.delete(find(targetId));
        events.userDeleted(targetId);
    }

    public UserStats stats() {
        Map<String, Long> byRole = new LinkedHashMap<>();
        Arrays.stream(Role.values()).forEach(r -> byRole.put(r.name().toLowerCase(), users.countByRole(r)));

        LocalDate today = LocalDate.now(ZoneOffset.UTC);
        Map<String, Long> signupsByDay = new TreeMap<>();
        for (int i = 29; i >= 0; i--) {
            signupsByDay.put(today.minusDays(i).toString(), 0L);
        }
        Instant since = today.minusDays(29).atStartOfDay(ZoneOffset.UTC).toInstant();
        users.findSignupTimesSince(since).forEach(t ->
                signupsByDay.merge(t.atZone(ZoneOffset.UTC).toLocalDate().toString(), 1L, Long::sum));

        return new UserStats(users.count(), byRole, signupsByDay);
    }

    private User find(long id) {
        return users.findById(id)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "User not found."));
    }

    private AuthResponse authResponse(User user) {
        return new AuthResponse(tokens.issue(user), "bearer", tokens.expirySeconds(), user.getId(),
                user.getUsername(), user.getEmail(), user.getRole().name().toLowerCase());
    }
}
