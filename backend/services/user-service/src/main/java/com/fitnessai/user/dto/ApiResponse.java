package com.fitnessai.user.dto;

// Same {status, message, data} envelope the existing React frontend parses
// (frontend/src/api.js), so switching it to the gateway is a base-URL change.
public record ApiResponse<T>(String status, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("success", null, data);
    }

    public static <T> ApiResponse<T> ok(String message, T data) {
        return new ApiResponse<>("success", message, data);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>("error", message, null);
    }
}
