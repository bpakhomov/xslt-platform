package com.example.xsltconverter.dto;

import io.swagger.v3.oas.annotations.media.Schema;

@Schema
public record UserResponseDto(
        @Schema Long id,
        @Schema String username
) {
}
