package com.example.xsltservice.dto.request;

import com.example.xsltservice.entity.ConversionType;

import java.time.LocalDateTime;

public record ConversionFilterDto(
        Integer limit,
        Integer offset,
        ConversionType conversionType,
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        Double conversionTimeFrom,
        Double conversionTimeTo,
        Integer inputElementsCountFrom,
        Integer inputElementsCountTo,
        Integer outputElementsCountFrom,
        Integer outputElementsCountTo
) {
    public ConversionFilterDto {
        if (limit == null) limit = 20;
        if (offset == null) offset = 0;
    }
}
