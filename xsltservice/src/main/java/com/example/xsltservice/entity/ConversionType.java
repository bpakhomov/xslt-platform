package com.example.xsltservice.entity;

import org.springframework.http.MediaType;

public enum ConversionType {
    XML_TO_JSON(MediaType.APPLICATION_JSON_VALUE, ".json"),
    XML_TO_XML(MediaType.APPLICATION_XML_VALUE, ".xml");

    private final String contentType;
    private final String fileExtension;

    ConversionType(String contentType, String fileExtension) {
        this.contentType = contentType;
        this.fileExtension = fileExtension;
    }

    public String getContentType() {
        return contentType;
    }

    public String getFileExtension() {
        return fileExtension;
    }
}
