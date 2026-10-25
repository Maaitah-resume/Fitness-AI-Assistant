package com.fitnessai.user.controller;

import com.fitnessai.user.dto.ApiResponse;
import com.fitnessai.user.dto.AuthDtos.AdminPasswordReset;
import com.fitnessai.user.dto.AuthDtos.RoleUpdateRequest;
import com.fitnessai.user.dto.AuthDtos.UserResponse;
import com.fitnessai.user.dto.AuthDtos.UserStats;
import com.fitnessai.user.service.UserService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('ADMIN')")
public class AdminUserController {

    private final UserService userService;

    public AdminUserController(UserService userService) {
        this.userService = userService;
    }

    @GetMapping({"", "/"})
    public ApiResponse<Map<String, List<UserResponse>>> list() {
        return ApiResponse.ok(Map.of("users", userService.listAll()));
    }

    @PatchMapping("/{id}/role")
    public ApiResponse<UserResponse> updateRole(@PathVariable long id, @Valid @RequestBody RoleUpdateRequest req,
                                                @AuthenticationPrincipal Jwt admin) {
        return ApiResponse.ok(userService.updateRole(id, req.role(), Long.parseLong(admin.getSubject())));
    }

    @PatchMapping("/{id}/password")
    public ApiResponse<Void> resetPassword(@PathVariable long id, @Valid @RequestBody AdminPasswordReset req) {
        userService.resetPassword(id, req.newPassword());
        return ApiResponse.ok("Password reset.", null);
    }

    @DeleteMapping("/{id}")
    public ApiResponse<Void> delete(@PathVariable long id, @AuthenticationPrincipal Jwt admin) {
        userService.delete(id, Long.parseLong(admin.getSubject()));
        return ApiResponse.ok("User " + id + " deleted.", null);
    }

    @GetMapping("/admin/stats")
    public ApiResponse<UserStats> stats() {
        return ApiResponse.ok(userService.stats());
    }
}
