package com.example.xsltconverter.service;

import com.example.xsltconverter.exception.XmlConversionException;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import java.io.StringReader;

final class XmlDocumentParser {

    private XmlDocumentParser() {
    }

    static Document parse(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            throw new XmlConversionException("XML content must not be empty");
        }
        try {
            return createBuilder().parse(new InputSource(new StringReader(xmlContent.trim())));
        } catch (Exception e) {
            throw new XmlConversionException("Invalid XML content", e);
        }
    }

    private static DocumentBuilder createBuilder() throws ParserConfigurationException {
        DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
        DocumentBuilder builder = factory.newDocumentBuilder();
        builder.setErrorHandler(null);
        return builder;
    }
}
