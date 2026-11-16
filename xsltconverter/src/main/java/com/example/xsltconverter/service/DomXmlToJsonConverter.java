package com.example.xsltconverter.service;

import com.example.xsltconverter.exception.XmlConversionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.w3c.dom.Element;
import org.w3c.dom.Node;
import org.xml.sax.InputSource;
import tools.jackson.databind.ObjectMapper;

import javax.xml.XMLConstants;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.StringReader;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class DomXmlToJsonConverter implements XmlToJsonConverter {

    private final ObjectMapper objectMapper;

    public DomXmlToJsonConverter() {
        this(new ObjectMapper());
    }

    @Autowired
    public DomXmlToJsonConverter(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public String convertXmlToJson(String xmlContent) {
        if (xmlContent == null || xmlContent.isBlank()) {
            throw new XmlConversionException("XML content must not be empty");
        }
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            var builder = factory.newDocumentBuilder();
            builder.setErrorHandler(null);
            Element root = builder.parse(new InputSource(new StringReader(xmlContent.trim()))).getDocumentElement();
            return objectMapper.writeValueAsString(Collections.singletonMap(root.getTagName(), elementToValue(root)));
        } catch (Exception e) {
            throw new XmlConversionException("Failed to convert XML to JSON", e);
        }
    }

    private Object elementToValue(Element element) {
        Map<String, Object> result = new LinkedHashMap<>();
        for (int i = 0; i < element.getAttributes().getLength(); i++) {
            Node attr = element.getAttributes().item(i);
            result.put("@" + attr.getNodeName(), attr.getNodeValue());
        }

        Map<String, List<Object>> children = new LinkedHashMap<>();
        StringBuilder text = new StringBuilder();
        for (Node child = element.getFirstChild(); child != null; child = child.getNextSibling()) {
            if (child.getNodeType() == Node.ELEMENT_NODE) {
                Element el = (Element) child;
                children.computeIfAbsent(el.getTagName(), _ -> new ArrayList<>()).add(elementToValue(el));
            } else if (child.getNodeType() == Node.TEXT_NODE || child.getNodeType() == Node.CDATA_SECTION_NODE) {
                text.append(child.getNodeValue());
            }
        }
        children.forEach((tag, items) -> result.put(tag, items.size() == 1 ? items.getFirst() : items));

        String trimmedText = text.toString().trim();
        if (result.isEmpty()) {
            return trimmedText.isEmpty() ? null : trimmedText;
        }
        if (!trimmedText.isEmpty()) {
            result.put("#text", trimmedText);
        }
        return result;
    }
}
