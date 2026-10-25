package com.fitnessai.user.controller;

import com.fitnessai.user.dto.ApiResponse;
import com.fitnessai.user.dto.AuthDtos.AuthResponse;
import com.fitnessai.user.dto.AuthDtos.DeleteAccountRequest;
import com.fitnessai.user.dto.AuthDtos.PasswordChangeRequest;
import com.fitnessai.user.dto.AuthDtos.LoginRequest;
import com.fitnessai.user.dto.AuthDtos.RegisterRequest;
import com.fitnessai.user.dto.AuthDtos.UserResponse;
import com.fitnessai.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final UserService userService;

    public AuthController(UserService userService) {
        this.userService = userService;
    }

    @PostMapping("/register")
    public ApiResponse<AuthResponse> register(@Valid @RequestBody RegisterRequest req) {
        return ApiResponse.ok("User registered successfully.", userService.register(req));
    }

    @PostMapping("/login")
    public ApiResponse<AuthResponse> login(@Valid @RequestBody LoginRequest req) {
        return ApiResponse.ok("Login successful.", userService.login(req));
    }

    @PostMapping("/logout")
    public ApiResponse<Void> logout(@AuthenticationPrincipal Jwt jwt) {
        userService.logout(jwt);
        return ApiResponse.ok("Logged out.", null);
    }

    @PostMapping("/password")
    public ApiResponse<Void> changePassword(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody PasswordChangeRequest req) {
        userService.changePassword(Long.parseLong(jwt.getSubject()), req);
        return ApiResponse.ok("Password changed.", null);
    }

    @DeleteMapping("/me")
    public ApiResponse<Void> deleteAccount(@AuthenticationPrincipal Jwt jwt, @Valid @RequestBody DeleteAccountRequest req) {
        userService.deleteSelf(jwt, req);
        return ApiResponse.ok("Account deleted.", null);
    }

    @GetMapping("/me")
    public ApiResponse<UserResponse> me(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.ok(userService.me(Long.parseLong(jwt.getSubject())));
    }
}
