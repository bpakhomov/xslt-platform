package com.example.xsltconverter.service;

import com.example.xsltconverter.exception.XmlConversionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DomXmlToJsonConverterTest {

    private DomXmlToJsonConverter converter;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        converter = new DomXmlToJsonConverter(objectMapper);
    }

    @Test
    void convertsSimpleXmlToJson() {
        String xml = """
            <user>
                <id>42</id>
                <name>Alice</name>
            </user>
            """;

        JsonNode root = objectMapper.readTree(converter.convertXmlToJson(xml));

        assertEquals("42", root.path("user").path("id").asString());
        assertEquals("Alice", root.path("user").path("name").asString());
    }

    @Test
    void convertsRepeatedSiblingTagsToArray() {
        String xml = """
            <catalog>
                <book><title>Clean Code</title></book>
                <book><title>Domain-Driven Design</title></book>
            </catalog>
            """;

        JsonNode books = objectMapper.readTree(converter.convertXmlToJson(xml))
            .path("catalog")
            .path("book");

        assertTrue(books.isArray());
        assertEquals(2, books.size());
        assertEquals("Clean Code", books.get(0).path("title").asString());
        assertEquals("Domain-Driven Design", books.get(1).path("title").asString());
    }

    @Test
    void convertsAttributesAndEmptyTag() {
        String xml = """
            <order id="10"><note>Say "Hi"</note><empty/></order>
            """;

        JsonNode order = objectMapper.readTree(converter.convertXmlToJson(xml)).path("order");

        assertEquals("10", order.path("@id").asString());
        assertEquals("Say \"Hi\"", order.path("note").asString());
        assertTrue(order.path("empty").isNull());
    }

    @Test
    void convertsEmptyRootToNullValue() {
        JsonNode root = objectMapper.readTree(converter.convertXmlToJson("<ping/>"));

        assertTrue(root.path("ping").isNull());
    }

    @Test
    void throwsOnInvalidOrBlankXml() {
        assertThrows(XmlConversionException.class, () -> converter.convertXmlToJson("<unclosed>"));
        assertThrows(XmlConversionException.class, () -> converter.convertXmlToJson("   "));
    }
}
