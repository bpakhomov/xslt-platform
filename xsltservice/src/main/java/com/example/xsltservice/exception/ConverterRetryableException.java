package com.example.xsltservice.exception;

public class ConverterRetryableException extends IllegalStateException {

    public ConverterRetryableException(String message) {
        super(message);
    }

    public ConverterRetryableException(String message, Throwable cause) {
        super(message, cause);
    }
}
