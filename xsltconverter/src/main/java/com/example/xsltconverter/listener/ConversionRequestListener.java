package com.example.xsltconverter.listener;

import com.example.xsltconverter.dto.ConversionKafkaRequest;
import com.example.xsltconverter.dto.ConversionKafkaResponse;
import com.example.xsltconverter.exception.XmlConversionException;
import com.example.xsltconverter.service.XmlConversionDispatcher;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.messaging.handler.annotation.SendTo;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.Optional;

@Component
@ConditionalOnProperty(name = "xsltconverter.transport", havingValue = "kafka")
public class ConversionRequestListener {
    private final XmlConversionDispatcher xmlConversionDispatcher;
    private final ObjectMapper objectMapper;

    public ConversionRequestListener(XmlConversionDispatcher xmlConversionDispatcher, ObjectMapper objectMapper) {
        this.xmlConversionDispatcher = xmlConversionDispatcher;
        this.objectMapper = objectMapper;
    }

    @KafkaListener(topics = "xml-conversion-request", groupId = "xsltconverter", concurrency = "1")
    @SendTo
    public String handle(String payload) {
        Optional<ConversionKafkaRequest> request = readRequest(payload);
        if (request.isEmpty()) {
            return writeResponse(new ConversionKafkaResponse(false, null, true, "Invalid payload"));
        }
        return writeResponse(convertSafely(request.get()));
    }

    private Optional<ConversionKafkaRequest> readRequest(String payload) {
        if (payload == null || payload.isBlank()) {
            return Optional.empty();
        }

        try {
            return Optional.of(objectMapper.readValue(payload, ConversionKafkaRequest.class));
        } catch (JacksonException e) {
            return Optional.empty();
        }
    }

    private ConversionKafkaResponse convertSafely(ConversionKafkaRequest conversionKafkaRequest) {
        try {
            String result = xmlConversionDispatcher.convert(
                    conversionKafkaRequest.xmlContent(),
                    conversionKafkaRequest.conversionType()
            );
            return new ConversionKafkaResponse(true, result, false, null);
        } catch (XmlConversionException e) {
            return new ConversionKafkaResponse(false, null, true, e.getMessage());
        } catch (Exception e) {
            return new ConversionKafkaResponse(false, null, false, e.getMessage());
        }
    }

    private String writeResponse(ConversionKafkaResponse conversionKafkaResponse) {
        try {
            return objectMapper.writeValueAsString(conversionKafkaResponse);
        } catch (JacksonException e) {
            throw new IllegalStateException("Failed to serialize Kafka conversion response");
        }
    }
}
