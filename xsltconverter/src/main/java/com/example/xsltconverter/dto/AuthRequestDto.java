package com.example.xsltconverter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema
public record AuthRequestDto(
        @Schema String username,
        @Schema String password
) {
}
