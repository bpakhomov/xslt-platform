package com.example.xsltconverter.service;

import com.example.xsltconverter.exception.XmlConversionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class XmlConversionDispatcherTest {

    private XmlConverter jsonConverter;
    private XmlConverter xmlConverter;
    private ConversionResultCache cache;
    private XmlConversionDispatcher dispatcher;

    @BeforeEach
    void setUp() {
        jsonConverter = mock(XmlConverter.class);
        when(jsonConverter.getType()).thenReturn(ConversionType.XML_TO_JSON);
        xmlConverter = mock(XmlConverter.class);
        when(xmlConverter.getType()).thenReturn(ConversionType.XML_TO_XML);
        cache = mock(ConversionResultCache.class);
        dispatcher = new XmlConversionDispatcher(List.of(jsonConverter, xmlConverter), cache);
    }

    @Nested
    class Convert {

        @Test
        void returnsCachedValueWithoutCallingConverter() {
            when(cache.get("<a/>", ConversionType.XML_TO_JSON)).thenReturn(Optional.of("{\"a\":null}"));

            String result = dispatcher.convert("<a/>", ConversionType.XML_TO_JSON);

            assertEquals("{\"a\":null}", result);
            verify(jsonConverter, never()).convert(anyString());
        }

        @Test
        void convertsAndCachesWhenCacheMisses() {
            when(cache.get("<a/>", ConversionType.XML_TO_XML)).thenReturn(Optional.empty());
            when(xmlConverter.convert("<a/>")).thenReturn("<a/>");

            String result = dispatcher.convert("<a/>", ConversionType.XML_TO_XML);

            assertEquals("<a/>", result);
            verify(cache).put("<a/>", ConversionType.XML_TO_XML, "<a/>");
        }

        @ParameterizedTest
        @NullAndEmptySource
        @ValueSource(strings = "   ")
        void throwsAndSkipsCacheWhenInputIsBlank(String xml) {
            assertThrows(XmlConversionException.class, () -> dispatcher.convert(xml, ConversionType.XML_TO_JSON));
            verifyNoInteractions(cache);
        }

        @Test
        void throwsWhenTypeIsNull() {
            assertThrows(XmlConversionException.class, () -> dispatcher.convert("<a/>", null));
            verifyNoInteractions(cache);
        }
    }
}
