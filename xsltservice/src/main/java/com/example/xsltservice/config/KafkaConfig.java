package com.example.xsltservice.config;

import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.listener.ConcurrentMessageListenerContainer;
import org.springframework.kafka.requestreply.ReplyingKafkaTemplate;

import java.time.Duration;

@Configuration
@ConditionalOnProperty(name = "xsltconverter.transport", havingValue = "kafka")
public class KafkaConfig {

    public static final String REQUEST_TOPIC = "xml-conversion-request";
    public static final String REPLY_TOPIC = "xml-conversion-reply";

    @Bean
    public NewTopic conversionRequestTopic() {
        return TopicBuilder.name(REQUEST_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public NewTopic conversionReplyTopic() {
        return TopicBuilder.name(REPLY_TOPIC).partitions(1).replicas(1).build();
    }

    @Bean
    public ConcurrentMessageListenerContainer<String, String> repliesContainer(
            ConcurrentKafkaListenerContainerFactory<String, String> factory
    ) {
        ConcurrentMessageListenerContainer<String, String> container = factory.createContainer((REPLY_TOPIC));
        container.getContainerProperties().setGroupId("xsltservice-replies");
        container.setAutoStartup(false);
        return container;
    }

    @Bean
    public ReplyingKafkaTemplate<String, String, String> replyingKafkaTemplate(
            ProducerFactory<String, String> producerFactory,
            ConcurrentMessageListenerContainer<String, String> repliesContainer
    ) {
        ReplyingKafkaTemplate<String, String, String> template =
                new ReplyingKafkaTemplate<>(producerFactory, repliesContainer);
        template.setDefaultReplyTimeout(Duration.ofSeconds(30));
        return template;
    }
}
