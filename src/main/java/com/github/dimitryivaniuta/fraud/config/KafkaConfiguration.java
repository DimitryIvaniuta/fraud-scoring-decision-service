package com.github.dimitryivaniuta.fraud.config;

import java.util.HashMap;
import java.util.Map;
import org.apache.kafka.clients.CommonClientConfigs;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;
import org.springframework.kafka.core.KafkaAdmin;
import reactor.kafka.receiver.ReceiverOptions;
import reactor.kafka.sender.KafkaSender;
import reactor.kafka.sender.SenderOptions;

/**
 * Creates Kafka producer, consumer, and topic beans for the outbox-backed event flow.
 */
@Configuration
@ConditionalOnProperty(prefix = "fraud.kafka", name = "enabled", havingValue = "true", matchIfMissing = true)
public class KafkaConfiguration {

    /**
     * Creates an admin client configuration used to auto-create development topics.
     *
     * @param properties application settings
     * @return Kafka admin bean
     */
    @Bean
    public KafkaAdmin kafkaAdmin(final FraudProperties properties) {
        return new KafkaAdmin(Map.of(AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
            properties.getKafka().getBootstrapServers()));
    }

    /**
     * Creates the decision topic with compact single-node-friendly defaults for local KRaft.
     *
     * @param properties application settings
     * @return topic definition
     */
    @Bean
    public NewTopic fraudDecisionTopic(final FraudProperties properties) {
        return TopicBuilder.name(properties.getKafka().getDecisionTopic())
            .partitions(3)
            .replicas(1)
            .build();
    }

    /**
     * Creates the enrichment request topic used by the asynchronous enrichment pipeline.
     *
     * @param properties application settings
     * @return topic definition
     */
    @Bean
    public NewTopic fraudEnrichmentTopic(final FraudProperties properties) {
        return TopicBuilder.name(properties.getKafka().getEnrichmentTopic())
            .partitions(3)
            .replicas(1)
            .build();
    }

    /**
     * Creates a Reactor Kafka sender that publishes JSON strings from the outbox.
     *
     * @param properties application settings
     * @return Kafka sender
     */
    @Bean
    public KafkaSender<String, String> kafkaSender(final FraudProperties properties) {
        Map<String, Object> senderProperties = new HashMap<>();
        senderProperties.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafka().getBootstrapServers());
        senderProperties.put(ProducerConfig.CLIENT_ID_CONFIG, properties.getKafka().getClientId() + "-producer");
        senderProperties.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        senderProperties.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        senderProperties.put(ProducerConfig.ACKS_CONFIG, "all");
        senderProperties.put(ProducerConfig.COMPRESSION_TYPE_CONFIG, "lz4");
        senderProperties.put(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, 120_000);
        senderProperties.put(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, true);
        senderProperties.put(ProducerConfig.LINGER_MS_CONFIG, 5);
        senderProperties.put(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, 5);
        senderProperties.put(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, 30_000);
        senderProperties.put(ProducerConfig.RETRIES_CONFIG, Integer.MAX_VALUE);
        return KafkaSender.create(SenderOptions.<String, String>create(senderProperties));
    }

    /**
     * Creates receiver options for enrichment request consumption.
     *
     * @param properties application settings
     * @return receiver options
     */
    @Bean
    public ReceiverOptions<String, String> enrichmentReceiverOptions(final FraudProperties properties) {
        Map<String, Object> receiverProperties = new HashMap<>();
        receiverProperties.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, properties.getKafka().getBootstrapServers());
        receiverProperties.put(ConsumerConfig.CLIENT_ID_CONFIG, properties.getKafka().getClientId() + "-enrichment-consumer");
        receiverProperties.put(ConsumerConfig.GROUP_ID_CONFIG, properties.getKafka().getClientId() + "-enrichment-group");
        receiverProperties.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        receiverProperties.put(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);
        receiverProperties.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        receiverProperties.put(ConsumerConfig.ENABLE_AUTO_COMMIT_CONFIG, false);
        receiverProperties.put(CommonClientConfigs.RETRY_BACKOFF_MS_CONFIG, 500);
        return ReceiverOptions.<String, String>create(receiverProperties)
            .subscription(java.util.List.of(properties.getKafka().getEnrichmentTopic()));
    }
}
