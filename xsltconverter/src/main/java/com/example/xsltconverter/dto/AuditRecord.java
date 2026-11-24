package com.example.xsltconverter.dto;

public record AuditRecord(
        String username,
        long userId,
        String httpMethod,
        String requestPath,
        String controllerMethod,
        String requestParameters,
        String requestBody,
        int responseStatus,
        String responseBody,
        String errorMessage,
        double durationMilliseconds
) {
    public static final long UNKNOWN_USER_ID = 0L;
}
