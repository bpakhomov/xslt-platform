package com.example.xsltservice.dto.request;

import java.time.LocalDateTime;

public record ConversionFilterDto(
        Integer limit,
        Integer offset,
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        Double conversionTimeFrom,
        Double conversionTimeTo,
        Integer xmlTagsCountFrom,
        Integer xmlTagsCountTo,
        Integer jsonKeysCountFrom,
        Integer jsonKeysCountTo
) {
    public ConversionFilterDto {
        if (limit == null) limit = 20;
        if (offset == null) offset = 0;
    }
}
