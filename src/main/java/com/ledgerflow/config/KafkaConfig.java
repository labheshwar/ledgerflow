package com.ledgerflow.config;

import com.ledgerflow.events.LedgerTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Topics are declared, not auto-created. Relying on Kafka's auto-creation
 * gives whatever default partition count the broker happens to have, which
 * decides the ordering and parallelism characteristics of a topic by
 * accident.
 *
 * Declared here rather than under a profile so whichever process starts
 * first creates them -- the web process publishes into the outbox before any
 * worker may exist.
 */
@Configuration
public class KafkaConfig {

    /**
     * Three partitions: enough to run three consumers in parallel, while
     * keying by organization keeps every event for one tenant on one
     * partition and therefore in order.
     *
     * Replication factor 1 because the local stack is a single broker. A real
     * deployment overrides this -- one replica means one broker failure loses
     * the log, which is exactly why the outbox rows are the durable record
     * and Kafka is the transport.
     */
    @Bean
    public NewTopic transactionsTopic() {
        return TopicBuilder.name(LedgerTopics.TRANSACTIONS)
                .partitions(3)
                .replicas(1)
                .build();
    }
}
