package com.example.xsltconverter.dto;

public record ConversionKafkaResponse(boolean success, String resultContent, boolean clientError, String errorMessage) {
}
