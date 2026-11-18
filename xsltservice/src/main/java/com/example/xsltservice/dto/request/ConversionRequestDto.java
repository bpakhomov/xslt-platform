package com.example.xsltservice.dto.request;

import com.example.xsltservice.entity.ConversionType;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema
public record ConversionRequestDto(
        @Schema String xmlContent,
        @Schema(defaultValue = "XML_TO_JSON") ConversionType conversionType
) {
    public ConversionRequestDto {
        if (conversionType == null) {
            conversionType = ConversionType.XML_TO_JSON;
        }
    }
}
