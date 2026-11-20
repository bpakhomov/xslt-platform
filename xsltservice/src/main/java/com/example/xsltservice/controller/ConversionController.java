package com.example.xsltservice.controller;

import com.example.xsltservice.dto.request.ConversionFilterDto;
import com.example.xsltservice.dto.request.ConversionRequestDto;
import com.example.xsltservice.dto.response.ConversionResponseDto;
import com.example.xsltservice.entity.User;
import com.example.xsltservice.security.AuthUser;
import com.example.xsltservice.service.ConversionService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import java.io.IOException;
import java.util.List;
import java.util.NoSuchElementException;

@RestController
public class ConversionController {

    private final ConversionService conversionService;

    public ConversionController(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @GetMapping(value = "/page", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Получить данные об обработанных запросах")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список конвертаций по фильтру и пагинации"),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры фильтрации или пагинации")
    })
    public ResponseEntity<List<ConversionResponseDto>> getConversions(@ParameterObject ConversionFilterDto filter) {
        return ResponseEntity.ok(conversionService.findByFilter(filter));
    }

    @PostMapping(value = "/request", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Преобразование XML в целевой формат с сохранением результата в БД для последующей миграции в S3")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "XML успешно сконвертирован и сохранен"),
            @ApiResponse(responseCode = "400", description = "Пустой или невалидный XML"),
            @ApiResponse(responseCode = "502", description = "Ошибка при обращении к сервису конвертации")
    })
    public ResponseEntity<ConversionResponseDto> convertXml(
            @RequestBody ConversionRequestDto request,
            @AuthenticationPrincipal AuthUser authUser
    ) throws IOException, InterruptedException {
        User currentUser = authUser != null ? authUser.getUser() : null;
        return ResponseEntity.ok(conversionService.convertXml(request, currentUser));
    }

    @GetMapping(value = "/request/{id}", produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Получить данные конвертации по id вместе с результатом")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Конвертация и ее результат получены"),
            @ApiResponse(responseCode = "404", description = "Конвертация с указанным id не найдена"),
            @ApiResponse(responseCode = "502", description = "Ошибка при обращении к S3")
    })
    public ResponseEntity<ConversionResponseDto> getConversion(@PathVariable Long id)
            throws IOException, InterruptedException {
        return ResponseEntity.ok(conversionService.getConversion(id));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler(NoSuchElementException.class)
    public ProblemDetail handleNotFound(NoSuchElementException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
    }

    @ExceptionHandler({IOException.class, IllegalStateException.class})
    public ProblemDetail handleConverterFailure(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }

    @ExceptionHandler(InterruptedException.class)
    public ProblemDetail handleInterrupted(InterruptedException ex) {
        Thread.currentThread().interrupt();
        return ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, "Request processing was interrupted");
    }
}
