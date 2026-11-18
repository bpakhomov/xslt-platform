package com.example.xsltconverter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema
public record XmlConvertRequestDto(
    @Schema(description = "XML-документ для конвертации в JSON") String xmlContent
) {}
