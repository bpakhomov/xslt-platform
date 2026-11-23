package com.example.xsltservice.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import tools.jackson.databind.ObjectMapper;

import java.net.http.HttpClient;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ConverterAuthClientTest {

    private HttpClient httpClient;
    private ConverterAuthClient authClient;

    @BeforeEach
    void setUp() {
        httpClient = mock(HttpClient.class);
        authClient = new ConverterAuthClient(
                httpClient,
                new ObjectMapper(),
                "http://localhost:8081",
                "xsltservice",
                "secret"
        );
    }

    @Nested
    class GetToken {

        @Test
        void fetchesAndCachesValidToken() throws Exception {
            String token = fakeJwtWithExp(Instant.now().plusSeconds(3600).getEpochSecond());
            HttpResponse<String> response = mockResponse(200, "{\"token\":\"" + token + "\"}");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(response);

            String first = authClient.getToken();
            String second = authClient.getToken();

            assertEquals(token, first);
            assertEquals(token, second);
            verify(httpClient, times(1)).send(any(), any());
        }

        @Test
        void throwsWhenLoginReturnsErrorStatus() throws Exception {
            HttpResponse<String> response = mockResponse(401, "Unauthorized");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(response);

            assertThrows(IllegalStateException.class, () -> authClient.getToken());
        }

        @Test
        void throwsWhenLoginReturnsEmptyToken() throws Exception {
            HttpResponse<String> response = mockResponse(200, "{\"token\":\"\"}");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(response);

            assertThrows(IllegalStateException.class, () -> authClient.getToken());
        }
    }

    @Nested
    class ForceRefreshToken {

        @Test
        void fetchesNewTokenBypassingCache() throws Exception {
            String firstJwt = fakeJwtWithExp(Instant.now().plusSeconds(3600).getEpochSecond());
            String secondJwt = fakeJwtWithExp(Instant.now().plusSeconds(7200).getEpochSecond());
            HttpResponse<String> firstResponse = mockResponse(200, "{\"token\":\"" + firstJwt + "\"}");
            HttpResponse<String> secondResponse = mockResponse(200, "{\"token\":\"" + secondJwt + "\"}");
            when(httpClient.send(any(), ArgumentMatchers.<HttpResponse.BodyHandler<String>>any()))
                    .thenReturn(firstResponse)
                    .thenReturn(secondResponse);

            String first = authClient.getToken();
            String refreshed = authClient.forceRefreshToken();

            assertEquals(firstJwt, first);
            assertEquals(secondJwt, refreshed);
            verify(httpClient, times(2)).send(any(), any());
        }
    }

    private static String fakeJwtWithExp(long expEpochSeconds) {
        String payload = Base64.getUrlEncoder().withoutPadding()
                .encodeToString(("{\"sub\":\"xsltservice\",\"exp\":" + expEpochSeconds + "}").getBytes(StandardCharsets.UTF_8));
        return "eyJhbGciOiJIUzI1NiJ9." + payload + ".sig";
    }

    @SuppressWarnings("unchecked")
    private static HttpResponse<String> mockResponse(int status, String body) {
        HttpResponse<String> response = mock(HttpResponse.class);
        when(response.statusCode()).thenReturn(status);
        when(response.body()).thenReturn(body);
        return response;
    }
}
