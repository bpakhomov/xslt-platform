package com.example.xsltservice.entity;

public enum TariffPlan {
    FREE(12),
    BASIC(30),
    PROFESSIONAL(60);

    private final long requestsPerMinute;

    TariffPlan(long requestsPerMinute) {
        this.requestsPerMinute = requestsPerMinute;
    }

    public long getRequestsPerMinute() {
        return requestsPerMinute;
    }
}
