package com.example.xsltservice.dto.response;

import com.example.xsltservice.entity.ConversionType;
import com.fasterxml.jackson.annotation.JsonInclude;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ConversionResponseDto(
        @Schema Long id,
        @Schema ConversionType conversionType,
        @Schema double conversionTime,
        @Schema LocalDateTime conversionDateTime,
        @Schema String sourceFileKey,
        @Schema String resultFileKey,
        @Schema Integer inputElementsCount,
        @Schema Integer outputElementsCount,
        @Schema String result,
        @Schema String username
) {}
