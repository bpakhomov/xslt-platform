package com.example.xsltservice.dto.response;

public record ConversionKafkaReply(boolean success, String resultContent, boolean clientError, String errorMessage) {
}
