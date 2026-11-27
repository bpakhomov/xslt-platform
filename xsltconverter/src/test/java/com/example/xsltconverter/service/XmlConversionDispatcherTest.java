package com.example.xsltconverter.service;

import com.example.xsltconverter.exception.XmlConversionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class XmlConversionDispatcherTest {

    private XmlConverter jsonConverter;
    private XmlConverter xmlConverter;
    private XmlConversionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        jsonConverter = mock(XmlConverter.class);
        when(jsonConverter.getType()).thenReturn(ConversionType.XML_TO_JSON);
        xmlConverter = mock(XmlConverter.class);
        when(xmlConverter.getType()).thenReturn(ConversionType.XML_TO_XML);
        dispatcher = new XmlConversionDispatcher(List.of(jsonConverter, xmlConverter));
    }

    @Nested
    class Convert {

        @Test
        void delegatesToMatchingConverterForXmlToJson() {
            when(jsonConverter.convert("<a/>")).thenReturn("{\"a\":null}");

            String result = dispatcher.convert("<a/>", ConversionType.XML_TO_JSON);

            assertEquals("{\"a\":null}", result);
            verify(jsonConverter).convert("<a/>");
            verify(xmlConverter, never()).convert(anyString());
        }

        @Test
        void delegatesToMatchingConverterForXmlToXml() {
            when(xmlConverter.convert("<a/>")).thenReturn("<a/>");

            String result = dispatcher.convert("<a/>", ConversionType.XML_TO_XML);

            assertEquals("<a/>", result);
            verify(xmlConverter).convert("<a/>");
            verify(jsonConverter, never()).convert(anyString());
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void throwsWhenInputIsBlank(String xml) {
            assertThrows(XmlConversionException.class, () -> dispatcher.convert(xml, ConversionType.XML_TO_JSON));
            verify(jsonConverter, never()).convert(anyString());
            verify(xmlConverter, never()).convert(anyString());
        }

        @Test
        void throwsWhenTypeIsNull() {
            assertThrows(XmlConversionException.class, () -> dispatcher.convert("<a/>", null));
            verify(jsonConverter, never()).convert(anyString());
            verify(xmlConverter, never()).convert(anyString());
        }
    }
}
