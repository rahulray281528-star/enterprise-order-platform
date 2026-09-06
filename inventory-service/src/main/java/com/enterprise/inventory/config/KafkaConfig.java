package com.enterprise.inventory.config;

import com.enterprise.common.constant.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Kafka wiring for inventory-service.
 *
 * <p>Topics are declared as beans so a fresh cluster is usable without any manual
 * setup. Consumer failures are retried a bounded number of times and then routed to a
 * per-topic dead-letter topic, so one poison message cannot block the partition
 * forever.</p>
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic inventoryReservedTopic() {
        return TopicBuilder.name(KafkaTopics.INVENTORY_RESERVED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryReservedDltTopic() {
        return TopicBuilder.name(KafkaTopics.INVENTORY_RESERVED + KafkaTopics.DLT_SUFFIX)
                .partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryReservationFailedTopic() {
        return TopicBuilder.name(KafkaTopics.INVENTORY_RESERVATION_FAILED).partitions(3).replicas(1).build();
    }

    @Bean
    public NewTopic inventoryReservationFailedDltTopic() {
        return TopicBuilder.name(KafkaTopics.INVENTORY_RESERVATION_FAILED + KafkaTopics.DLT_SUFFIX)
                .partitions(3).replicas(1).build();
    }


    @Bean
    public DefaultErrorHandler kafkaErrorHandler(KafkaTemplate<String, Object> template) {
        // Publish to "<original-topic>.DLT", keeping the original partition.
        DeadLetterPublishingRecoverer recoverer = new DeadLetterPublishingRecoverer(template,
                (record, exception) -> new TopicPartition(
                        record.topic() + KafkaTopics.DLT_SUFFIX, record.partition()));

        // 3 attempts, 1 second apart, then dead-letter.
        DefaultErrorHandler handler = new DefaultErrorHandler(recoverer, new FixedBackOff(1000L, 2L));
        handler.setCommitRecovered(true);
        return handler;
    }
}
