package com.example.xsltconverter.service;

import org.springframework.http.MediaType;

public enum ConversionType {
    XML_TO_JSON(MediaType.APPLICATION_JSON),
    XML_TO_XML(MediaType.APPLICATION_XML);

    private final MediaType responseMediaType;

    ConversionType(MediaType responseMediaType) {
        this.responseMediaType = responseMediaType;
    }

    public MediaType getResponseMediaType() {
        return responseMediaType;
    }
}
