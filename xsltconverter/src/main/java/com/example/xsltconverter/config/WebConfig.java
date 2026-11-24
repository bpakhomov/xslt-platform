package com.example.xsltconverter.config;

import com.example.xsltconverter.interceptor.AuditInterceptor;
import com.example.xsltconverter.service.ConversionType;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.format.FormatterRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String[] EXCLUDED_AUDIT_PATHS = {
            "/auth/**",
            "/swagger.html",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/error"
    };

    private final AuditInterceptor auditInterceptor;

    public WebConfig(AuditInterceptor auditInterceptor) {
        this.auditInterceptor = auditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(auditInterceptor).excludePathPatterns(EXCLUDED_AUDIT_PATHS);
    }

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
