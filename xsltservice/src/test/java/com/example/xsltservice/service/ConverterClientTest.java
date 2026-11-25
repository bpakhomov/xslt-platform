package com.example.xsltservice.service;

import com.example.xsltservice.entity.ConversionType;
import com.example.xsltservice.exception.ConverterRetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentMatchers;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConverterClientTest {

    private HttpClient httpClient;
    private ConverterClient client;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        client = new ConverterClient(httpClient, new ObjectMapper(), "http://localhost:8081/", "", "");
    }

    @Nested
    class Convert {

        @Test
        void returnsBodyWhenConverterRespondsOk() throws Exception {
            HttpResponse<String> okResponse = mockResponse(200, "{\"a\":null}");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(okResponse);

            String result = client.convert("<a/>", ConversionType.XML_TO_JSON);

            assertEquals("{\"a\":null}", result);
        }

        @Test
        void refreshesTokenAndRetriesOnUnauthorized() throws Exception {
            ConverterClient authClient = new ConverterClient(
                    httpClient,
                    new ObjectMapper(),
                    "http://localhost:8081",
                    "xsltservice",
                    "secret"
            );
            HttpResponse<String> staleToken = mockResponse(200, "{\"token\":\"stale\"}");
            HttpResponse<String> unauthorized = mockResponse(401, "");
            HttpResponse<String> freshToken = mockResponse(200, "{\"token\":\"fresh\"}");
            HttpResponse<String> okResponse = mockResponse(200, "{\"ok\":true}");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(staleToken)
                    .thenReturn(unauthorized)
                    .thenReturn(freshToken)
                    .thenReturn(okResponse);

            String result = authClient.convert("<a/>", ConversionType.XML_TO_JSON);

            assertEquals("{\"ok\":true}", result);
            verify(httpClient, times(4)).send(any(), any());
        }

        @ParameterizedTest
        @ValueSource(ints = {429, 500, 502, 503})
        void throwsRetryableExceptionOnRateLimitOrServerError(int status) throws Exception {
            HttpResponse<String> errorResponse = mockResponse(status, "temporarily unavailable");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(errorResponse);

            assertThrows(
                    ConverterRetryableException.class,
                    () -> client.convert("<a/>", ConversionType.XML_TO_JSON)
            );
        }

        @Test
        void throwsNonRetryableIllegalStateOnBadRequest() throws Exception {
            HttpResponse<String> badRequest = mockResponse(400, "invalid xml");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(badRequest);

            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> client.convert("<broken>", ConversionType.XML_TO_JSON)
            );
            assertFalse(exception instanceof ConverterRetryableException);
        }

        @Test
        void propagatesNetworkIoException() throws Exception {
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenThrow(new IOException("Connection reset"));

            assertThrows(IOException.class, () -> client.convert("<a/>", ConversionType.XML_TO_JSON));
        }
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }
}
