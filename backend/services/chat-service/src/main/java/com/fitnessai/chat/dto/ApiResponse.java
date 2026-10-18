package com.fitnessai.chat.dto;

/** Same envelope every service returns; also used to read downstream responses. */
public record ApiResponse<T>(String status, String message, T data) {

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>("success", null, data);
    }

    public static ApiResponse<Void> error(String message) {
        return new ApiResponse<>("error", message, null);
    }
}
