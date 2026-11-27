package com.example.xsltservice.service;

import com.example.xsltservice.entity.ConversionType;
import com.example.xsltservice.exception.ConverterRetryableException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Optional;

@Component
@ConditionalOnProperty(name = "xsltconverter.transport", havingValue = "rest", matchIfMissing = true)
public class RestConverterClient implements ConverterClient {

    private static final Logger log = LoggerFactory.getLogger(RestConverterClient.class);
    private static final String XML_FORMAT_PATH = "/xml2format?type=";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(10);
    private static final int HTTP_UNAUTHORIZED = 401;
    private static final int HTTP_BAD_REQUEST_THRESHOLD = 400;
    private static final int HTTP_TOO_MANY_REQUESTS = 429;
    private static final int HTTP_SERVER_ERROR_THRESHOLD = 500;
    private static final TokenSupplier NO_AUTH = new NoOpTokenSupplier();

    private final HttpClient httpClient;
    private final String xsltconverterUrl;
    private final TokenSupplier tokenSupplier;

    @Autowired
    public RestConverterClient(
            ObjectMapper objectMapper,
            @Value("${xsltconverter.url}") String xsltconverterUrl,
            @Value("${xsltconverter.auth.username:}") String converterUsername,
            @Value("${xsltconverter.auth.password:}") String converterPassword
    ) {
        this(
                HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build(),
                objectMapper,
                xsltconverterUrl,
                converterUsername,
                converterPassword
        );
    }

    RestConverterClient(
            HttpClient httpClient,
            ObjectMapper objectMapper,
            String xsltconverterUrl,
            String converterUsername,
            String converterPassword
    ) {
        this.httpClient = httpClient;
        String baseUrl = stripTrailingSlash(xsltconverterUrl);
        this.xsltconverterUrl = baseUrl;
        this.tokenSupplier = buildTokenSupplier(
                httpClient,
                objectMapper,
                new AuthSettings(baseUrl, converterUsername, converterPassword)
        );
    }

    @Retryable(
            includes = {ConverterRetryableException.class, IOException.class},
            maxRetriesString = "${xsltconverter.retry.max-retries:3}",
            delayString = "${xsltconverter.retry.delay-ms:1100}",
            multiplierString = "${xsltconverter.retry.multiplier:1.5}",
            maxDelayString = "${xsltconverter.retry.max-delay-ms:3000}"
    )
    public String convert(String xmlContent, ConversionType conversionType)
            throws IOException, InterruptedException {
        HttpResponse<String> response = sendWithAuth(xmlContent, conversionType);
        return extractBodyOrThrow(response);
    }

    private TokenSupplier buildTokenSupplier(
            HttpClient client,
            ObjectMapper objectMapper,
            AuthSettings settings
    ) {
        if (!hasText(settings.username()) || !hasText(settings.password())) {
            return NO_AUTH;
        }
        ConverterAuthClient authClient = new ConverterAuthClient(
                client,
                objectMapper,
                settings.baseUrl(),
                settings.username(),
                settings.password()
        );
        return new JwtTokenSupplier(authClient);
    }

    private HttpResponse<String> sendWithAuth(String xmlContent, ConversionType conversionType)
            throws IOException, InterruptedException {
        HttpRequest.Builder builder = createRequestBuilder(xmlContent, conversionType);
        tokenSupplier.getToken().ifPresent(token -> builder.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + token));
        HttpResponse<String> response = sendHttpRequest(builder.build());
        if (response.statusCode() == HTTP_UNAUTHORIZED) {
            return retryWithRefreshedToken(xmlContent, conversionType, response);
        }
        return response;
    }

    private HttpResponse<String> retryWithRefreshedToken(
            String xmlContent,
            ConversionType conversionType,
            HttpResponse<String> unauthorizedResponse
    ) throws IOException, InterruptedException {
        Optional<String> refreshedToken = tokenSupplier.refreshToken();
        if (refreshedToken.isEmpty()) {
            return unauthorizedResponse;
        }
        HttpRequest.Builder builder = createRequestBuilder(xmlContent, conversionType);
        builder.header(HttpHeaders.AUTHORIZATION, BEARER_PREFIX + refreshedToken.get());
        return sendHttpRequest(builder.build());
    }

    private HttpRequest.Builder createRequestBuilder(String xmlContent, ConversionType conversionType) {
        return HttpRequest.newBuilder()
                .uri(URI.create(xsltconverterUrl + XML_FORMAT_PATH + conversionType.name()))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_XML_VALUE)
                .header(HttpHeaders.ACCEPT, conversionType.getContentType())
                .POST(HttpRequest.BodyPublishers.ofString(xmlContent, StandardCharsets.UTF_8));
    }

    private HttpResponse<String> sendHttpRequest(HttpRequest request) throws IOException, InterruptedException {
        try {
            return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        } catch (IOException exception) {
            log.warn("Network error calling xsltconverter: {}", exception.getMessage());
            throw exception;
        }
    }

    private String extractBodyOrThrow(HttpResponse<String> response) {
        int status = response.statusCode();
        if (isRetryableStatus(status)) {
            String message = formatErrorMessage(status, response.body());
            log.warn("Retryable error from xsltconverter: {}", message);
            throw new ConverterRetryableException(message);
        }
        if (status >= HTTP_BAD_REQUEST_THRESHOLD) {
            throw new IllegalStateException(formatErrorMessage(status, response.body()));
        }
        return response.body();
    }

    private boolean isRetryableStatus(int status) {
        return status == HTTP_TOO_MANY_REQUESTS || status >= HTTP_SERVER_ERROR_THRESHOLD;
    }

    private String formatErrorMessage(int status, String body) {
        return "xsltconverter returned HTTP " + status + ": " + body;
    }

    private static String stripTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }

    private static boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private record AuthSettings(String baseUrl, String username, String password) {
    }

    private interface TokenSupplier {
        Optional<String> getToken() throws IOException, InterruptedException;

        Optional<String> refreshToken() throws IOException, InterruptedException;
    }

    private static final class NoOpTokenSupplier implements TokenSupplier {
        @Override
        public Optional<String> getToken() {
            return Optional.empty();
        }

        @Override
        public Optional<String> refreshToken() {
            return Optional.empty();
        }
    }

    private static final class JwtTokenSupplier implements TokenSupplier {
        private final ConverterAuthClient authClient;

        private JwtTokenSupplier(ConverterAuthClient authClient) {
            this.authClient = authClient;
        }

        @Override
        public Optional<String> getToken() throws IOException, InterruptedException {
            return Optional.of(authClient.getToken());
        }

        @Override
        public Optional<String> refreshToken() throws IOException, InterruptedException {
            return Optional.of(authClient.forceRefreshToken());
        }
    }
}
