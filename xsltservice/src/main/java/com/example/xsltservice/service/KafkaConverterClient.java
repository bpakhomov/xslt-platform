package com.example.xsltservice.service;

import com.example.xsltservice.config.KafkaConfig;
import com.example.xsltservice.dto.request.ConversionKafkaRequest;
import com.example.xsltservice.dto.response.ConversionKafkaReply;
import com.example.xsltservice.entity.ConversionType;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.concurrent.ExecutionException;

@Component
@ConditionalOnProperty(name = "xsltconverter.transport", havingValue = "kafka")
public class KafkaConverterClient implements ConverterClient {
    private final ReplyingKafkaTemplate<String, String, String> replyingKafkaTemplate;
    private final ObjectMapper objectMapper;

    @Autowired
    public KafkaConverterClient(ReplyingKafkaTemplate<String, String, String> replyingKafkaTemplate, ObjectMapper objectMapper) {
        this.replyingKafkaTemplate = replyingKafkaTemplate;
        this.objectMapper = objectMapper;
    }

    @Override
    public String convert(String xmlContent, ConversionType conversionType) throws InterruptedException {
        if (xmlContent == null || xmlContent.isBlank())
        {
            throw new IllegalArgumentException("XML content must not be empty");
        }
        if (conversionType == null) {
            throw new IllegalArgumentException("Conversion type must not be null");
        }
        ConversionKafkaReply reply = sendKafkaRequest(new ConversionKafkaRequest(xmlContent, conversionType));
        if (reply.success()) {
            return reply.resultContent();
        }
        if (reply.clientError()) {
            throw new IllegalArgumentException(reply.errorMessage());
        }
        throw new IllegalStateException(reply.errorMessage());
    }

    private ConversionKafkaReply
    sendKafkaRequest(ConversionKafkaRequest request)
            throws InterruptedException {
        try {
            String payload = objectMapper.writeValueAsString(request);
            ConsumerRecord<String, String> record = replyingKafkaTemplate.sendAndReceive(
                    new ProducerRecord<>(KafkaConfig.REQUEST_TOPIC, payload)
            ).get();

            if (record == null || record.value() == null || record.value().isBlank()) {
                throw new IllegalStateException("Empty reply from xsltconverter via Kafka");
            }

            return objectMapper.readValue(record.value(), ConversionKafkaReply.class);
        } catch (JacksonException | ExecutionException e) {
            throw new IllegalStateException("Kafka conversion request failed", e);
        }
    }
}
