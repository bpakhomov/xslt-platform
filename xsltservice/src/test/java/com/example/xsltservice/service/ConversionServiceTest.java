package com.example.xsltservice.service;

import com.example.xsltservice.dao.ConversionDao;
import com.example.xsltservice.dao.ConversionFilter;
import com.example.xsltservice.dto.request.ConversionFilterDto;
import com.example.xsltservice.dto.request.ConversionRequestDto;
import com.example.xsltservice.dto.response.ConversionResponseDto;
import com.example.xsltservice.entity.Conversion;
import com.example.xsltservice.entity.ConversionType;
import com.example.xsltservice.mapper.ConversionMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.time.LocalDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class ConversionServiceTest {

    private ConversionDao dao;
    private HttpClient httpClient;
    private ConversionService service;

    @BeforeEach
    void setUp() {
        dao = mock(ConversionDao.class);
        httpClient = mock(HttpClient.class);
        when(dao.save(any(Conversion.class))).thenAnswer(inv -> inv.getArgument(0));
        service = new ConversionService(
                dao,
                new ObjectMapper(),
                new ConversionMapper(),
                new TransactionTemplate(mock(PlatformTransactionManager.class)),
                "http://localhost:8081",
                "http://localhost:8082",
                httpClient,
                null,
                null,
                100
        );
    }

    @Nested
    class ConvertXml {

        @Test
        void convertsXmlToXmlAndSavesToDb() throws Exception {
            String inputXml = "<example><param1>123</param1><inner><param2>456</param2></inner></example>";
            String outputXml = "<example param1=\"123\"><inner param2=\"456\"/></example>";
            HttpResponse<String> okResponse = mockResponse(200, outputXml);
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(okResponse);

            ConversionResponseDto response = service.convertXml(
                    new ConversionRequestDto(inputXml, ConversionType.XML_TO_XML)
            );

            assertEquals(ConversionType.XML_TO_XML, response.conversionType());
            assertEquals(4, response.inputElementsCount());
            assertEquals(2, response.outputElementsCount());
            assertEquals(outputXml, response.result());
            verify(dao).save(argThat(saved ->
                    inputXml.equals(saved.getSourceContent()) && outputXml.equals(saved.getResultContent())
            ));
        }

        @Test
        void defaultsTypeToXmlToJsonWhenOmitted() throws Exception {
            String inputXml = "<user><name>Alice</name></user>";
            String outputJson = "{\"user\":{\"name\":\"Alice\"}}";
            HttpResponse<String> okResponse = mockResponse(200, outputJson);
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(okResponse);

            ConversionResponseDto response = service.convertXml(new ConversionRequestDto(inputXml, null));

            assertEquals(ConversionType.XML_TO_JSON, response.conversionType());
            assertEquals(2, response.inputElementsCount());
            assertEquals(2, response.outputElementsCount());
            assertEquals(outputJson, response.result());
        }

        @Test
        void refreshesTokenAndRetriesWhenConverterReturnsUnauthorized() throws Exception {
            ConversionService authService = new ConversionService(
                    dao,
                    new ObjectMapper(),
                    new ConversionMapper(),
                    new TransactionTemplate(mock(PlatformTransactionManager.class)),
                    "http://localhost:8081",
                    "http://localhost:8082",
                    httpClient,
                    "xsltservice",
                    "secret",
                    100
            );
            HttpResponse<String> staleTokenResponse = mockResponse(200, "{\"token\":\"stale-token\"}");
            HttpResponse<String> unauthorizedResponse = mockResponse(401, "");
            HttpResponse<String> freshTokenResponse = mockResponse(200, "{\"token\":\"fresh-token\"}");
            HttpResponse<String> okResponse = mockResponse(200, "{\"a\":null}");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(staleTokenResponse)
                    .thenReturn(unauthorizedResponse)
                    .thenReturn(freshTokenResponse)
                    .thenReturn(okResponse);

            ConversionResponseDto result = authService.convertXml(
                    new ConversionRequestDto("<a/>", ConversionType.XML_TO_JSON)
            );

            assertEquals("{\"a\":null}", result.result());
            verify(httpClient, times(4)).send(any(), any());
        }

        @Test
        void throwsWhenRequestIsNull() {
            assertThrows(IllegalArgumentException.class, () -> service.convertXml(null));
            verifyNoInteractions(dao, httpClient);
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void throwsWhenXmlContentIsBlank(String xml) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> service.convertXml(new ConversionRequestDto(xml, ConversionType.XML_TO_JSON))
            );
            verifyNoInteractions(dao, httpClient);
        }
    }

    @Nested
    class GetConversion {

        @Test
        void returnsResultFromDbWhenPresent() throws Exception {
            Conversion pending = conversion(1L, null, "{\"a\":null}");
            when(dao.findById(1L)).thenReturn(Optional.of(pending));

            ConversionResponseDto result = service.getConversion(1L);

            assertEquals("{\"a\":null}", result.result());
            verifyNoInteractions(httpClient);
        }

        @Test
        void downloadsResultFromS3WhenNotInDb() throws Exception {
            Conversion migrated = conversion(3L, "3-result.xml", null);
            when(dao.findById(3L)).thenReturn(Optional.of(migrated));
            HttpResponse<String> okResponse = mockResponse(200, "<from-s3/>");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(okResponse);

            ConversionResponseDto result = service.getConversion(3L);

            assertEquals("<from-s3/>", result.result());
        }

        @Test
        void throwsWhenConversionNotFound() {
            when(dao.findById(99L)).thenReturn(Optional.empty());

            assertThrows(NoSuchElementException.class, () -> service.getConversion(99L));
        }
    }

    @Nested
    class FindByFilter {

        private final ConversionFilterDto validFilter =
                new ConversionFilterDto(10, 0, null, null, null, null, null, null, null, null, null);

        @Test
        void returnsEmptyListWhenDaoReturnsEmpty() {
            when(dao.findByFilter(any(ConversionFilter.class))).thenReturn(List.of());

            List<ConversionResponseDto> page = service.findByFilter(validFilter);

            assertTrue(page.isEmpty());
            verifyNoInteractions(httpClient);
        }

        @Test
        void returnsResultFromDbDirectly() {
            Conversion entry = conversion(1L, null, "<db/>");
            when(dao.findByFilter(any(ConversionFilter.class))).thenReturn(List.of(entry));

            List<ConversionResponseDto> page = service.findByFilter(validFilter);

            assertEquals("<db/>", page.getFirst().result());
            verifyNoInteractions(httpClient);
        }

        @Test
        void downloadsFromS3WhenResultContentMissing() {
            Conversion entry = conversion(3L, "3-result.xml", null);
            when(dao.findByFilter(any(ConversionFilter.class))).thenReturn(List.of(entry));
            HttpResponse<String> okResponse = mockResponse(200, "<from-s3/>");
            when(httpClient.sendAsync(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(CompletableFuture.completedFuture(okResponse));

            List<ConversionResponseDto> page = service.findByFilter(validFilter);

            assertEquals("<from-s3/>", page.getFirst().result());
        }

        @Test
        void returnsNullResultWhenS3Fails() {
            Conversion entry = conversion(4L, "missing.xml", null);
            when(dao.findByFilter(any(ConversionFilter.class))).thenReturn(List.of(entry));
            HttpResponse<String> notFoundResponse = mockResponse(404, "Not found");
            when(httpClient.sendAsync(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(CompletableFuture.completedFuture(notFoundResponse));

            List<ConversionResponseDto> page = service.findByFilter(validFilter);

            assertNull(page.getFirst().result());
        }

        @Test
        void throwsWhenFilterIsNull() {
            assertThrows(IllegalArgumentException.class, () -> service.findByFilter(null));
            verifyNoInteractions(dao);
        }

        @ParameterizedTest
        @ValueSource(ints = {0, -1})
        void throwsWhenLimitIsNotPositive(int limit) {
            ConversionFilterDto badFilter =
                    new ConversionFilterDto(limit, 0, null, null, null, null, null, null, null, null, null);

            assertThrows(IllegalArgumentException.class, () -> service.findByFilter(badFilter));
            verifyNoInteractions(dao);
        }

        @Test
        void throwsWhenOffsetIsNegative() {
            ConversionFilterDto badFilter =
                    new ConversionFilterDto(10, -1, null, null, null, null, null, null, null, null, null);

            assertThrows(IllegalArgumentException.class, () -> service.findByFilter(badFilter));
            verifyNoInteractions(dao);
        }
    }

    @Nested
    class MigrateConversionsToS3 {

        @Test
        void uploadsContentsAndUpdatesKeysInBatch() throws Exception {
            Conversion pending = conversion(7L, null, "{\"ping\":null}");
            pending.setSourceContent("<ping/>");
            when(dao.getConversionsForMigration(100)).thenReturn(List.of(pending));
            HttpResponse<String> okResponse = mockResponse(200, "ok");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(okResponse);

            service.migrateConversionsToS3();

            verify(httpClient, times(2)).send(any(), any());
            verify(dao).updateConversionsAfterMigration(argThat(items ->
                    items.size() == 1
                            && items.getFirst().id().equals(7L)
                            && items.getFirst().sourceKey().equals("7-source.xml")
                            && items.getFirst().resultKey().equals("7-result.json")
            ));
        }

        @Test
        void doesNothingWhenNoPendingConversions() {
            when(dao.getConversionsForMigration(100)).thenReturn(List.of());

            service.migrateConversionsToS3();

            verifyNoInteractions(httpClient);
            verify(dao, never()).updateConversionsAfterMigration(any());
        }
    }

    private static Conversion conversion(Long id, String resultFileKey, String resultContent) {
        Conversion conversion = spy(new Conversion(
                ConversionType.XML_TO_JSON,
                2.0,
                LocalDateTime.of(2026, 11, 20, 11, 0),
                null,
                resultFileKey,
                1,
                1,
                null,
                resultContent
        ));
        when(conversion.getId()).thenReturn(id);
        return conversion;
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockResponse(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
