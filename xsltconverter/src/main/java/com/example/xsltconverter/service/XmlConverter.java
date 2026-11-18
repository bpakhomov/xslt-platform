package com.example.xsltconverter.service;

public interface XmlConverter {
    ConversionType getType();

    String convert(String xmlContent);
}
