package com.example.xsltconverter.controller;

import com.example.xsltconverter.dto.XmlConvertRequestDto;
import com.example.xsltconverter.exception.XmlConversionException;
import com.example.xsltconverter.service.XmlToJsonConverter;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ConversionController {

    private final XmlToJsonConverter converter;

    public ConversionController(XmlToJsonConverter converter) {
        this.converter = converter;
    }

    @PostMapping(
        value = "/xml2format",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Конвертация XML в формат JSON")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "XML успешно сконвертирован в JSON"),
        @ApiResponse(responseCode = "400", description = "Пустой или невалидный XML")
    })
    public ResponseEntity<String> convertJsonBody(@RequestBody XmlConvertRequestDto request) {
        return ResponseEntity.ok(converter.convertXmlToJson(request.xmlContent()));
    }

    @PostMapping(
        value = "/xml2format",
        consumes = {MediaType.APPLICATION_XML_VALUE, MediaType.TEXT_XML_VALUE, MediaType.TEXT_PLAIN_VALUE},
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    @Operation(summary = "Конвертация сырого XML в формат JSON")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "XML успешно сконвертирован в JSON"),
        @ApiResponse(responseCode = "400", description = "Пустой или невалидный XML")
    })
    public ResponseEntity<String> convertRawXml(@RequestBody String xmlContent) {
        return ResponseEntity.ok(converter.convertXmlToJson(xmlContent));
    }

    @ExceptionHandler(XmlConversionException.class)
    public ProblemDetail handleConversionError(XmlConversionException ex) {
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
    }
}
