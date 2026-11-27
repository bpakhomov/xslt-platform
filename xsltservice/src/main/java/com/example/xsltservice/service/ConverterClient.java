package com.example.xsltservice.service;

import com.example.xsltservice.entity.ConversionType;

import java.io.IOException;

public interface ConverterClient {
    String convert(String xmlContent, ConversionType conversionType)
            throws IOException, InterruptedException ;
}
