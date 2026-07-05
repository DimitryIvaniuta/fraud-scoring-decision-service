package com.github.dimitryivaniuta.fraud.config;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe application settings for fraud scoring, cache access, and Kafka integration.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "fraud")
public class FraudProperties {

    /** Cache-related settings used by the low-latency scoring path. */
    private Cache cache = new Cache();

    /** Kafka and outbox publishing settings. */
    private Kafka kafka = new Kafka();

    /** Deterministic scoring rule weights and thresholds. */
    private Scoring scoring = new Scoring();

    /**
     * Cache settings for Redis feature lookup and deterministic fallback values.
     */
    @Getter
    @Setter
    public static class Cache {

        /** Default account age used when feature data is unavailable. */
        private int fallbackAccountAgeDays = 90;

        /** Default average amount used when feature data is unavailable. */
        private BigDecimal fallbackAverageAmount = new BigDecimal("100.00");

        /** Per-operation timeout for Redis and feature-store lookups. */
        private Duration operationTimeout = Duration.ofMillis(40);

        /** Redis time-to-live for feature snapshots. */
        private Duration ttl = Duration.ofMinutes(20);
    }

    /**
     * Kafka settings for event publishing, topic names, and outbox polling.
     */
    @Getter
    @Setter
    public static class Kafka {

        /** Comma-separated Kafka bootstrap servers. */
        private String bootstrapServers = "localhost:9092";

        /** Client identifier used by producers and consumers. */
        private String clientId = "fraud-scoring-service";

        /** Topic that receives persisted decision events. */
        private String decisionTopic = "fraud.decision.made";

        /** Switch that disables Kafka components for fast tests or local debugging. */
        private boolean enabled = true;

        /** Topic that receives enrichment requests from the synchronous decision API. */
        private String enrichmentTopic = "fraud.enrichment.requested";

        /** Maximum attempts before an outbox event is moved to dead letter state. */
        private int outboxMaxAttempts = 10;

        /** Delay between outbox polling runs. */
        private Duration outboxPollDelay = Duration.ofMillis(500);

        /** Maximum outbox records to publish per polling cycle. */
        private int outboxPollLimit = 50;

        /** Temporary claim duration before another node may recover a stuck event. */
        private Duration outboxProcessingTtl = Duration.ofMinutes(2);

        /** Maximum number of concurrent outbox publishes per polling cycle. */
        private int outboxPublishConcurrency = 8;
    }

    /**
     * Scoring settings that keep the decision model deterministic and explainable.
     */
    @Getter
    @Setter
    public static class Scoring {

        /** Stable model version stored with each decision for audit and replay. */
        private String modelVersion = "rules-2026-07-04";

        /** Weight applied to chargeback rate contribution. */
        private int chargebackWeight = 30;

        /** Minimum score that produces an automatic decline. */
        private int declineThreshold = 80;

        /** ISO country codes considered higher risk for this example service. */
        private List<String> highRiskCountries = List.of("RU", "BY", "IR", "KP", "SY");

        /** Weight applied when the transaction country is high risk. */
        private int highRiskCountryWeight = 25;

        /** Weight applied per recent prior decline. */
        private int priorDeclineWeight = 5;

        /** Minimum score that routes the transaction to manual review. */
        private int reviewThreshold = 55;

        /** Velocity threshold over which the transaction is considered suspicious. */
        private int velocityThreshold24h = 8;

        /** Weight applied when velocity exceeds the configured threshold. */
        private int velocityWeight = 22;

        /** Multiplier over average amount that marks a transaction as unusual. */
        private double unusualAmountMultiplier = 4.0d;

        /** Weight applied when amount is unusually high for the customer and merchant. */
        private int unusualAmountWeight = 28;
    }
}
