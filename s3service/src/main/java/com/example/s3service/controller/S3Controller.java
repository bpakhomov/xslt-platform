package com.example.s3service.controller;

import com.example.s3service.service.MinioStorageService;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.MinioException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/s3")
public class S3Controller {

    private final MinioStorageService storageService;

    public S3Controller(MinioStorageService storageService) {
        this.storageService = storageService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE, produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Загрузить файл в S3")
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Файл успешно загружен"),
            @ApiResponse(responseCode = "400", description = "Некорректные параметры файла"),
            @ApiResponse(responseCode = "502", description = "Ошибка при обращении к MinIO")
    })
    public ResponseEntity<String> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String key
    ) throws Exception {
        String objectKey = (key != null && !key.isBlank())
                ? key
                : (file.getOriginalFilename() != null && !file.getOriginalFilename().isBlank()
                        ? file.getOriginalFilename()
                        : UUID.randomUUID().toString());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(storageService.upload(objectKey, file.getInputStream(), file.getSize(), file.getContentType()));
    }

    @PutMapping(value = "/{key}", produces = MediaType.TEXT_PLAIN_VALUE)
    @Operation(summary = "Загрузить сырые байты в S3 по ключу")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Файл успешно загружен"),
            @ApiResponse(responseCode = "400", description = "Некорректный ключ или пустое тело"),
            @ApiResponse(responseCode = "502", description = "Ошибка при обращении к MinIO")
    })
    public ResponseEntity<String> uploadBytes(
            @PathVariable String key,
            @RequestBody(required = false) byte[] content,
            @RequestHeader(value = HttpHeaders.CONTENT_TYPE, required = false) String contentType
    ) throws Exception {
        if (key.isBlank() || content == null || content.length == 0) {
            throw new IllegalArgumentException("Key and content must not be empty");
        }
        String storedKey = storageService.upload(key, new ByteArrayInputStream(content), content.length, contentType);
        return ResponseEntity.ok(storedKey);
    }

    @GetMapping("/{key}")
    @Operation(summary = "Скачать файл из S3")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Файл успешно получен"),
            @ApiResponse(responseCode = "404", description = "Файл не найден"),
            @ApiResponse(responseCode = "502", description = "Ошибка при обращении к MinIO")
    })
    public ResponseEntity<byte[]> download(@PathVariable String key) throws Exception {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(key).build().toString())
                .body(storageService.download(key));
    }

    @GetMapping(produces = MediaType.APPLICATION_JSON_VALUE)
    @Operation(summary = "Список файлов в бакете")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Список ключей объектов в бакете"),
            @ApiResponse(responseCode = "502", description = "Ошибка при обращении к MinIO")
    })
    public ResponseEntity<List<String>> list() throws Exception {
        return ResponseEntity.ok(storageService.list());
    }

    @DeleteMapping("/{key}")
    @Operation(summary = "Удалить файл из S3")
    @ApiResponses({
            @ApiResponse(responseCode = "204", description = "Файл удален"),
            @ApiResponse(responseCode = "404", description = "Файл или бакет не найден"),
            @ApiResponse(responseCode = "502", description = "Ошибка при обращении к MinIO")
    })
    public ResponseEntity<Void> delete(@PathVariable String key) throws Exception {
        storageService.delete(key);
        return ResponseEntity.noContent().build();
    }

    @ExceptionHandler(ErrorResponseException.class)
    public ProblemDetail handleMinioError(ErrorResponseException ex) {
        String code = ex.errorResponse() != null ? ex.errorResponse().code() : null;
        HttpStatus status = ("NoSuchKey".equals(code) || "NoSuchBucket".equals(code))
                ? HttpStatus.NOT_FOUND
                : HttpStatus.BAD_GATEWAY;
        return ProblemDetail.forStatusAndDetail(status, ex.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleBadRequest(IllegalArgumentException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }

    @ExceptionHandler({MinioException.class, IOException.class, GeneralSecurityException.class})
    public ProblemDetail handleStorageFailure(Exception ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_GATEWAY, ex.getMessage());
    }
}
