package com.example.xsltservice.dto.request;

import com.example.xsltservice.entity.ConversionType;

public record ConversionKafkaRequest(String xmlContent, ConversionType conversionType) {
}
