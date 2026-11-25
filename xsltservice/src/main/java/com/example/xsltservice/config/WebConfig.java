package com.example.xsltservice.config;

import com.example.xsltservice.interceptor.AuditInterceptor;
import com.example.xsltservice.interceptor.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    private static final String[] EXCLUDED_PATHS = {
            "/auth/**",
            "/swagger-ui.html",
            "/swagger-ui/**",
            "/v3/api-docs/**",
            "/v3/api-docs.yaml",
            "/error"
    };

    private final RateLimitInterceptor rateLimitInterceptor;
    private final AuditInterceptor auditInterceptor;

    public WebConfig(RateLimitInterceptor rateLimitInterceptor, AuditInterceptor auditInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
        this.auditInterceptor = auditInterceptor;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .excludePathPatterns(EXCLUDED_PATHS)
                .order(0);
        registry.addInterceptor(auditInterceptor)
                .excludePathPatterns(EXCLUDED_PATHS)
                .order(1);
    }
}
