package com.example.xsltservice.dto.request;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema
public record ConversionRequestDto(
    @Schema String xmlContent
) {}
