package com.example.xsltconverter.dto;

import com.example.xsltconverter.service.ConversionType;

public record ConversionKafkaRequest(String xmlContent, ConversionType conversionType) {
}
