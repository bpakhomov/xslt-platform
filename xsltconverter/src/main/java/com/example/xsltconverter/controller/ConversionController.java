package com.example.xsltconverter.controller;

import com.example.xsltconverter.dto.XmlConvertRequestDto;
import com.example.xsltconverter.exception.RateLimitExceededException;
import com.example.xsltconverter.exception.XmlConversionException;
import com.example.xsltconverter.service.ConversionRateLimiter;
import com.example.xsltconverter.service.ConversionType;
import com.example.xsltconverter.service.XmlConversionDispatcher;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

@RestController
public class ConversionController {

    private final XmlConversionDispatcher dispatcher;
    private final ConversionRateLimiter rateLimiter;

    public ConversionController(XmlConversionDispatcher dispatcher, ConversionRateLimiter rateLimiter) {
        this.dispatcher = dispatcher;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping(
        value = "/xml2format",
        consumes = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Конвертация XML в целевой формат")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "XML успешно преобразован"),
        @ApiResponse(responseCode = "400", description = "Пустой, невалидный XML или неподдерживаемый тип преобразования"),
        @ApiResponse(responseCode = "429", description = "Превышен лимит запросов к /xml2format")
    })
    public ResponseEntity<String> convertJsonBody(
            @RequestParam(defaultValue = "XML_TO_JSON") ConversionType type,
            @RequestBody XmlConvertRequestDto request
    ) {
        return convertAndRespond(request.xmlContent(), type);
    }

    @PostMapping(
        value = "/xml2format",
        consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, MediaType.TEXT_PLAIN_VALUE}
    )
    @Operation(summary = "Конвертация сырого XML в целевой формат")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "XML успешно преобразован"),
        @ApiResponse(responseCode = "400", description = "Пустой, невалидный XML или неподдерживаемый тип преобразования"),
        @ApiResponse(responseCode = "429", description = "Превышен лимит запросов к /xml2format")
    })
    public ResponseEntity<String> convertRawXml(
            @RequestParam(defaultValue = "XML_TO_JSON") ConversionType type,
            @RequestBody String xmlContent
    ) {
        return convertAndRespond(xmlContent, type);
    }

    private ResponseEntity<String> convertAndRespond(String xmlContent, ConversionType type) {
        rateLimiter.acquirePermit();
        return ResponseEntity.ok()
                .contentType(type.getResponseMediaType())
                .body(dispatcher.convert(xmlContent, type));
    }

    @ExceptionHandler({XmlConversionException.class, MethodArgumentTypeMismatchException.class})
    public ProblemDetail handleConversionError(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(RateLimitExceededException.class)
    public ResponseEntity<ProblemDetail> handleRateLimitExceeded(RateLimitExceededException exception) {
        ProblemDetail problemDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.TOO_MANY_REQUESTS,
                exception.getMessage()
        );
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(problemDetail);
    }
}
