package com.example.xsltconverter.service;

import com.example.xsltconverter.exception.XmlConversionException;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Service
public class XmlConversionDispatcher {

    private final Map<ConversionType, XmlConverter> converters = new EnumMap<>(ConversionType.class);

    public XmlConversionDispatcher(List<XmlConverter> converterList) {
        for (XmlConverter converter : converterList) {
            XmlConverter existing = converters.put(converter.getType(), converter);
            if (existing != null) {
                throw new IllegalStateException("Duplicate converter for type: " + converter.getType());
            }
        }
    }

    @Cacheable(
            value = "conversions",
            key = "#type.name() + ':' + T(org.springframework.util.DigestUtils).md5DigestAsHex(#xmlContent.trim().getBytes())",
            condition = "#xmlContent != null && !#xmlContent.isBlank() && #type != null"
    )
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

        return converter.convert(xmlContent);
    }
}
