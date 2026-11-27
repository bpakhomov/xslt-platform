package com.example.xsltservice.service;

import com.example.xsltservice.entity.ConversionType;
import com.example.xsltservice.exception.ConverterRetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.resilience.annotation.EnableResilientMethods;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@SpringJUnitConfig(RestConverterClientRetryProxyTest.RetryConfig.class)
@TestPropertySource(properties = {
        "xsltconverter.retry.max-retries=3",
        "xsltconverter.retry.delay-ms=5",
        "xsltconverter.retry.multiplier=1.0",
        "xsltconverter.retry.max-delay-ms=10"
})
class RestConverterClientRetryProxyTest {

    @Autowired
    private RestConverterClient restConverterClient;

    @Autowired
    private HttpClient httpClient;

    @BeforeEach
    void setUp() {
        reset(httpClient);
    }

    @Nested
    class RetryBehavior {

        @Test
        void retriesTooManyRequestsAndReturnsSuccessWhenLaterAttemptSucceeds() throws Exception {
            HttpResponse<String> tooManyRequests = mockResponse(429, "rate limited");
            HttpResponse<String> okResponse = mockResponse(200, "{\"ok\":true}");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(tooManyRequests)
                    .thenReturn(tooManyRequests)
                    .thenReturn(okResponse);

            String result = restConverterClient.convert("<a/>", ConversionType.XML_TO_JSON);

            assertEquals("{\"ok\":true}", result);
            verify(httpClient, times(3)).send(any(), any());
        }

        @Test
        void stopsAfterMaxRetriesWhenTooManyRequestsPersists() throws Exception {
            HttpResponse<String> tooManyRequests = mockResponse(429, "rate limited");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(tooManyRequests);

            assertThrows(
                    ConverterRetryableException.class,
                    () -> restConverterClient.convert("<a/>", ConversionType.XML_TO_JSON)
            );
            verify(httpClient, times(4)).send(any(), any());
        }

        @Test
        void doesNotRetryOnBadRequest() throws Exception {
            HttpResponse<String> badRequest = mockResponse(400, "invalid xml");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(badRequest);

            assertThrows(
                    IllegalStateException.class,
                    () -> restConverterClient.convert("<broken>", ConversionType.XML_TO_JSON)
            );
            verify(httpClient, times(1)).send(any(), any());
        }
    }

    @SuppressWarnings("unchecked")
    private HttpResponse<String> mockResponse(int statusCode, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(statusCode);
        when(response.body()).thenReturn(body);
        return response;
    }

    @Configuration
    @EnableResilientMethods(proxyTargetClass = true)
    static class RetryConfig {

        @Bean
        HttpClient httpClient() {
            return mock(HttpClient.class);
        }

        @Bean
        RestConverterClient converterClient(HttpClient httpClient) {
            return new RestConverterClient(httpClient, new ObjectMapper(), "http://localhost:8081", "", "");
        }
    }
}
