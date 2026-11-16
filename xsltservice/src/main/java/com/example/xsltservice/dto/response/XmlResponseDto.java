package com.example.xsltservice.dto.response;

import com.fasterxml.jackson.annotation.JsonRawValue;
import io.swagger.v3.oas.annotations.media.Schema;

import java.time.LocalDateTime;

@Schema
public record XmlResponseDto(

    @Schema Long id,

    @Schema double conversionTime,

    @Schema LocalDateTime conversionDateTime,

    @Schema @JsonRawValue String jsonValue,

    @Schema Integer xmlTagsCount,

    @Schema Integer jsonKeysCount

) {}
