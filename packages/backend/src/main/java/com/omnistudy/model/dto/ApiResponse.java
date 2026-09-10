package com.omnistudy.model.dto;

public record ApiResponse<T>(
    boolean success,
    T data,
    String error,
    String code,
    String traceId
) {
    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, data, null, null, null);
    }

    public static <T> ApiResponse<T> fail(String error) {
        return new ApiResponse<>(false, null, error, null, null);
    }

    public static <T> ApiResponse<T> fail(String code, String error) {
        return new ApiResponse<>(false, null, error, code, null);
    }

    public static <T> ApiResponse<T> fail(String code, String error, String traceId) {
        return new ApiResponse<>(false, null, error, code, traceId);
    }
}
