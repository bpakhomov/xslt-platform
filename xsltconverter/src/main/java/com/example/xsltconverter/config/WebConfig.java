package com.example.xsltconverter.config;

import com.example.xsltconverter.service.ConversionType;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addFormatters(FormatterRegistry registry) {
        registry.addConverter(new StringToConversionTypeConverter());
    }

    private static final class StringToConversionTypeConverter implements Converter<String, ConversionType> {
        @Override
        public ConversionType convert(String source) {
            try {
                return ConversionType.valueOf(source.trim().toUpperCase());
            } catch (IllegalArgumentException e) {
                throw new IllegalArgumentException("Unsupported conversion type: " + source);
            }
        }
    }
}
