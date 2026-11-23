package com.example.xsltconverter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema
public record AuthTokenResponseDto(
        @Schema String token
) {
}
