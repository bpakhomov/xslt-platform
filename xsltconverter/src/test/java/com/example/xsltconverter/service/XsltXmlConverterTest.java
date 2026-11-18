package com.example.xsltconverter.service;

import com.example.xsltconverter.exception.XmlConversionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class XsltXmlConverterTest {

    private XsltXmlConverter converter;

    @BeforeEach
    void setUp() {
        converter = new XsltXmlConverter();
    }

    @Test
    void convertsNestedElementsIntoAttributesAsExpected() {
        String xml = """
                <example>
                  <param1>123</param1>
                  <param2>123</param2>
                  <param3>123</param3>
                  <param4>123</param4>
                  <inner>
                    <param1>123</param1>
                    <param2>123</param2>
                    <param3>123</param3>
                    <param4>123</param4>
                  </inner>
                </example>
                """;

        String result = converter.convert(xml);
        Document doc = XmlDocumentParser.parse(result);
        Element root = doc.getDocumentElement();

        assertEquals("example", root.getTagName());
        assertEquals("123", root.getAttribute("param1"));
        assertEquals("123", root.getAttribute("param2"));
        assertEquals("123", root.getAttribute("param3"));
        assertEquals("123", root.getAttribute("param4"));

        NodeList children = root.getElementsByTagName("inner");
        assertEquals(1, children.getLength());
        Element inner = (Element) children.item(0);
        assertEquals("123", inner.getAttribute("param1"));
        assertEquals("123", inner.getAttribute("param4"));
        assertEquals(0, inner.getElementsByTagName("*").getLength());
    }

    @Test
    void preservesDuplicateSiblingTagsAndExistingAttributes() {
        String xml = """
                <catalog version="2">
                  <title>Books</title>
                  <tag>java</tag>
                  <tag>xslt</tag>
                  <item id="10"><name>Clean Code</name></item>
                </catalog>
                """;

        Document doc = XmlDocumentParser.parse(converter.convert(xml));
        Element root = doc.getDocumentElement();

        assertEquals("2", root.getAttribute("version"));
        assertEquals("Books", root.getAttribute("title"));
        assertEquals(2, root.getElementsByTagName("tag").getLength());
        Element item = (Element) root.getElementsByTagName("item").item(0);
        assertEquals("10", item.getAttribute("id"));
        assertEquals("Clean Code", item.getAttribute("name"));
    }

    @Test
    void handlesSingleLeafRootAndInvalidXml() {
        String single = converter.convert("<ping>ok</ping>");
        assertTrue(single.contains("<ping>ok</ping>"));

        assertThrows(XmlConversionException.class, () -> converter.convert("<broken>"));
        assertThrows(XmlConversionException.class, () -> converter.convert("  "));
    }
}
