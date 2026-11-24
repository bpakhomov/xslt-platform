package com.example.xsltconverter.service;

import com.example.xsltconverter.dao.AuditDao;
import com.example.xsltconverter.dto.AuditRecord;
import com.example.xsltconverter.entity.AuditLog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

@Service
public class AuditService {

    static final int MAX_BODY_LENGTH = 10_000;
    static final int MAX_PARAMETER_LENGTH = 2_000;
    static final int MAX_PATH_LENGTH = 1_024;
    static final int MAX_NAME_LENGTH = 255;
    static final String TRUNCATION_SUFFIX = "...(truncated)";

    private static final Logger log = LoggerFactory.getLogger(AuditService.class);

    private final AuditDao auditDao;
    private final TransactionTemplate transactionTemplate;

    @Autowired
    public AuditService(AuditDao auditDao, TransactionTemplate transactionTemplate) {
        this.auditDao = auditDao;
        this.transactionTemplate = transactionTemplate;
    }

    public void record(AuditRecord auditRecord) {
        if (auditRecord == null) {
            throw new IllegalArgumentException("Audit record must not be null");
        }
        AuditRecord normalized = normalizeRecord(auditRecord);
        AuditLog entry = AuditLog.from(normalized, LocalDateTime.now());
        saveSafely(entry);
    }

    private AuditRecord normalizeRecord(AuditRecord auditRecord) {
        return new AuditRecord(
                truncate(auditRecord.username(), MAX_NAME_LENGTH),
                auditRecord.userId(),
                auditRecord.httpMethod(),
                truncate(auditRecord.requestPath(), MAX_PATH_LENGTH),
                truncate(auditRecord.controllerMethod(), MAX_NAME_LENGTH),
                truncate(auditRecord.requestParameters(), MAX_PARAMETER_LENGTH),
                truncate(auditRecord.requestBody(), MAX_BODY_LENGTH),
                auditRecord.responseStatus(),
                truncate(auditRecord.responseBody(), MAX_BODY_LENGTH),
                truncate(auditRecord.errorMessage(), MAX_BODY_LENGTH),
                auditRecord.durationMilliseconds()
        );
    }

    private String truncate(String value, int maxLength) {
        if (value == null) {
            return "";
        }
        if (value.length() <= maxLength) {
            return value;
        }
        int contentLimit = Math.max(0, maxLength - TRUNCATION_SUFFIX.length());
        return value.substring(0, contentLimit) + TRUNCATION_SUFFIX;
    }

    private void saveSafely(AuditLog entry) {
        try {
            transactionTemplate.executeWithoutResult(_ -> auditDao.save(entry));
        } catch (RuntimeException exception) {
            log.warn(
                    "Failed to persist audit log for {} {}: {}",
                    entry.getHttpMethod(),
                    entry.getRequestPath(),
                    exception.getMessage()
            );
        }
    }
}
