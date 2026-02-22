package com.ufo.ufo.global.response;

public record ApiResponse<T>(T data, ErrorDetail error) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(data, null);
    }

    public static <T> ApiResponse<T> error(String code, String message) {
        return new ApiResponse<>(null, new ErrorDetail(code, message));
    }

    public record ErrorDetail(String code, String message) {
    }
}
