package com.example.xsltconverter.service;

import com.example.xsltconverter.dao.AuditDao;
import com.example.xsltconverter.dto.AuditRecord;
import com.example.xsltconverter.entity.AuditLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class AuditServiceTest {

    private AuditDao auditDao;
    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditDao = mock(AuditDao.class);
        TransactionTemplate transactionTemplate = new TransactionTemplate(mock(PlatformTransactionManager.class));
        auditService = new AuditService(auditDao, transactionTemplate);
    }

    @Nested
    class Record {

        @Test
        void savesAuditLogFromRecord() {
            AuditRecord record = new AuditRecord(
                    "alice", 1L, "POST", "/xml2format", "ConversionController#convertRawXml",
                    "type=XML_TO_JSON", "<a/>", 200, "{\"a\":1}", "", 8.3
            );

            auditService.record(record);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditDao).save(captor.capture());
            AuditLog saved = captor.getValue();
            assertEquals("alice", saved.getUsername());
            assertEquals(1L, saved.getUserId());
            assertEquals("ConversionController#convertRawXml", saved.getControllerMethod());
            assertEquals("<a/>", saved.getRequestBody());
            assertEquals(200, saved.getResponseStatus());
            assertEquals("{\"a\":1}", saved.getResponseBody());
            assertNull(saved.getErrorMessage());
            assertNotNull(saved.getCreatedAt());
        }

        @Test
        void truncatesLongRequestAndResponseBody() {
            String largeBody = "x".repeat(AuditService.MAX_BODY_LENGTH + 500);
            AuditRecord record = new AuditRecord(
                    "alice", 1L, "POST", "/xml2format", "ConversionController#convertRawXml",
                    "", largeBody, 200, largeBody, "", 5.0
            );

            auditService.record(record);

            ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
            verify(auditDao).save(captor.capture());
            AuditLog saved = captor.getValue();
            assertEquals(AuditService.MAX_BODY_LENGTH, saved.getRequestBody().length());
            assertEquals(AuditService.MAX_BODY_LENGTH, saved.getResponseBody().length());
            assertTrue(saved.getRequestBody().endsWith(AuditService.TRUNCATION_SUFFIX));
        }

        @Test
        void doesNotThrowWhenDaoFails() {
            AuditRecord record = new AuditRecord(
                    "alice", 1L, "POST", "/xml2format", "ConversionController#convertRawXml",
                    "", "<a/>", 200, "{}", "", 2.0
            );
            doThrow(new RuntimeException("DB error")).when(auditDao).save(any(AuditLog.class));

            assertDoesNotThrow(() -> auditService.record(record));
            verify(auditDao).save(any(AuditLog.class));
        }
    }
}
