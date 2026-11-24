package com.example.xsltconverter.entity;

import com.example.xsltconverter.dto.AuditRecord;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "audit_log")
public class AuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "username", nullable = false, length = 255)
    private String username;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "http_method", nullable = false, length = 16)
    private String httpMethod;

    @Column(name = "request_path", nullable = false, length = 1024)
    private String requestPath;

    @Column(name = "controller_method", nullable = false, length = 255)
    private String controllerMethod;

    @Column(name = "request_parameters", nullable = false, columnDefinition = "TEXT")
    private String requestParameters;

    @Column(name = "request_body", nullable = false, columnDefinition = "TEXT")
    private String requestBody;

    @Column(name = "response_status", nullable = false)
    private int responseStatus;

    @Column(name = "response_body", nullable = false, columnDefinition = "TEXT")
    private String responseBody;

    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Column(name = "duration_ms", nullable = false)
    private double durationMilliseconds;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public AuditLog() {
    }

    private AuditLog(AuditRecord record, LocalDateTime createdAt) {
        this.username = record.username();
        this.userId = record.userId() > AuditRecord.UNKNOWN_USER_ID ? record.userId() : null;
        this.httpMethod = record.httpMethod();
        this.requestPath = record.requestPath();
        this.controllerMethod = record.controllerMethod();
        this.requestParameters = record.requestParameters();
        this.requestBody = record.requestBody();
        this.responseStatus = record.responseStatus();
        this.responseBody = record.responseBody();
        this.errorMessage = record.errorMessage().isEmpty() ? null : record.errorMessage();
        this.durationMilliseconds = record.durationMilliseconds();
        this.createdAt = createdAt;
    }

    public static AuditLog from(AuditRecord record, LocalDateTime createdAt) {
        return new AuditLog(record, createdAt);
    }

    public Long getId() {
        return id;
    }

    public String getUsername() {
        return username;
    }

    public Long getUserId() {
        return userId;
    }

    public String getHttpMethod() {
        return httpMethod;
    }

    public String getRequestPath() {
        return requestPath;
    }

    public String getControllerMethod() {
        return controllerMethod;
    }

    public String getRequestParameters() {
        return requestParameters;
    }

    public String getRequestBody() {
        return requestBody;
    }

    public int getResponseStatus() {
        return responseStatus;
    }

    public String getResponseBody() {
        return responseBody;
    }

    public String getErrorMessage() {
        return errorMessage;
    }

    public double getDurationMilliseconds() {
        return durationMilliseconds;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
}
