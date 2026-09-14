package com.ledgerflow.outbox;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties("ledgerflow.outbox")
public record OutboxProperties(
        /** How many rows one poll claims. Bounded so a backlog drains in steady batches. */
        @DefaultValue("500") int batchSize,
        @DefaultValue("30") int publishTimeoutSeconds,
        Datasource datasource) {

    /**
     * The privileged identity described in V11. Separate from
     * spring.datasource on purpose -- it is the one connection in the system
     * that can see across organizations.
     */
    public record Datasource(String url, String username, String password) {}
}
