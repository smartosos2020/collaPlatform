package com.colla.platform.shared.errors;

public record ApiErrorResponse(ApiError error, String requestId) {

    public record ApiError(String code, String message) {
    }
}

