package com.example.xsltconverter.exception;

public class XmlConversionException extends RuntimeException {

    public XmlConversionException(String message, Throwable cause) {
        super(message, cause);
    }

    public XmlConversionException(String message) {
        super(message);
    }
}
