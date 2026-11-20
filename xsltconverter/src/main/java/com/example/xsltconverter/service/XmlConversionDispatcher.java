package com.example.xsltconverter.service;

import com.example.xsltconverter.exception.XmlConversionException;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@Service
public class XmlConversionDispatcher {

    private final Map<ConversionType, XmlConverter> converters = new EnumMap<>(ConversionType.class);
    private final ConversionResultCache cache;

    public XmlConversionDispatcher(List<XmlConverter> converterList, ConversionResultCache cache) {
        this.cache = cache;
        for (XmlConverter converter : converterList) {
            XmlConverter existing = converters.put(converter.getType(), converter);
            if (existing != null) {
                throw new IllegalStateException("Duplicate converter for type: " + converter.getType());
            }
        }
    }

    public String convert(String xmlContent, ConversionType type) {
        if (type == null) {
            throw new XmlConversionException("Conversion type must not be null");
        }
        XmlConverter converter = converters.get(type);
        if (converter == null) {
            throw new XmlConversionException("Unsupported conversion type: " + type);
        }
        if (xmlContent == null || xmlContent.isBlank()) {
            throw new XmlConversionException("XML content must not be empty");
        }

        Optional<String> cached = cache.get(xmlContent, type);
        if (cached.isPresent()) {
            return cached.get();
        }

        String result = converter.convert(xmlContent);
        cache.put(xmlContent, type, result);
        return result;
    }
}
