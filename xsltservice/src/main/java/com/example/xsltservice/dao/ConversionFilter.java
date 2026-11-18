package com.example.xsltservice.dao;

import com.example.xsltservice.entity.ConversionType;

import java.time.LocalDateTime;

public record ConversionFilter(
        int limit,
        int offset,
        ConversionType conversionType,
        LocalDateTime dateFrom,
        LocalDateTime dateTo,
        Double conversionTimeFrom,
        Double conversionTimeTo,
        Integer inputElementsCountFrom,
        Integer inputElementsCountTo,
        Integer outputElementsCountFrom,
        Integer outputElementsCountTo
) {}
