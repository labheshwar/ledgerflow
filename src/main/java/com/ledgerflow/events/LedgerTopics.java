package com.ledgerflow.events;

/**
 * Topic names carry an explicit version suffix because a Kafka topic is a
 * published interface. Consumers outlive producers, so a breaking change to
 * an event shape is published as .v2 alongside .v1 rather than silently
 * changing what .v1 means underneath whoever is still reading it. Additive
 * changes stay on the same topic and are handled by schemaVersion in the
 * envelope.
 */
public final class LedgerTopics {

    public static final String TRANSACTIONS = "ledger.transactions.v1";

    private LedgerTopics() {}
}
