package com.example.xsltservice.jobs;

import com.example.xsltservice.service.ConversionService;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
public class ConversionArchiveJob {

    private final ConversionService conversionService;

    public ConversionArchiveJob(ConversionService conversionService) {
        this.conversionService = conversionService;
    }

    @Scheduled(fixedDelayString = "${migration.delay-ms}")
    public void migrateToS3() {
        conversionService.migrateConversionsToS3();
    }
}
