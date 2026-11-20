package com.example.xsltconverter.service;

import com.example.xsltconverter.exception.XmlConversionException;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class XmlConversionDispatcherTest {

    @Test
    void returnsCachedValueWithoutCallingConverter() {
        XmlConverter converter = mock(XmlConverter.class);
        when(converter.getType()).thenReturn(ConversionType.XML_TO_JSON);
        ConversionResultCache cache = mock(ConversionResultCache.class);
        when(cache.get("<a/>", ConversionType.XML_TO_JSON)).thenReturn(Optional.of("{\"a\":null}"));

        XmlConversionDispatcher dispatcher = new XmlConversionDispatcher(List.of(converter), cache);

        assertEquals("{\"a\":null}", dispatcher.convert("<a/>", ConversionType.XML_TO_JSON));
        verify(converter, never()).convert("<a/>");
    }

    @Test
    void convertsAndCachesWhenCacheMisses() {
        XmlConverter converter = mock(XmlConverter.class);
        when(converter.getType()).thenReturn(ConversionType.XML_TO_XML);
        when(converter.convert("<a/>")).thenReturn("<a/>");
        ConversionResultCache cache = mock(ConversionResultCache.class);
        when(cache.get("<a/>", ConversionType.XML_TO_XML)).thenReturn(Optional.empty());

        XmlConversionDispatcher dispatcher = new XmlConversionDispatcher(List.of(converter), cache);

        assertEquals("<a/>", dispatcher.convert("<a/>", ConversionType.XML_TO_XML));
        verify(cache).put("<a/>", ConversionType.XML_TO_XML, "<a/>");
    }

    @Test
    void doesNotTouchCacheWhenInputBlank() {
        XmlConverter converter = mock(XmlConverter.class);
        when(converter.getType()).thenReturn(ConversionType.XML_TO_JSON);
        ConversionResultCache cache = mock(ConversionResultCache.class);

        XmlConversionDispatcher dispatcher = new XmlConversionDispatcher(List.of(converter), cache);

        assertThrows(XmlConversionException.class, () -> dispatcher.convert("  ", ConversionType.XML_TO_JSON));
        verifyNoInteractions(cache);
    }
}
