package com.ledgerflow.outbox;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Worker-only. The web process records outbox rows but never drains them, so
 * it never opens the privileged connection and never runs a scheduler.
 *
 * Scheduling is enabled here rather than on the application class for the
 * same reason: turning it on globally would mean every future @Scheduled job
 * silently runs once per web replica as well as on the worker.
 */
@Configuration
@Profile("worker")
@EnableScheduling
@EnableConfigurationProperties(OutboxProperties.class)
class OutboxConfig {

    @Bean
    OutboxStore outboxStore(OutboxProperties properties) {
        return new OutboxStore(properties.datasource());
    }
}
