package com.example.xsltservice.dao;

import java.time.LocalDateTime;

public record ConversionFilter(
        int limit,
        int offset,
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        Double conversionTimeFrom,
        Double conversionTimeTo,
        Integer xmlTagsCountFrom,
        Integer xmlTagsCountTo,
        Integer jsonKeysCountFrom,
        Integer jsonKeysCountTo
) {}
