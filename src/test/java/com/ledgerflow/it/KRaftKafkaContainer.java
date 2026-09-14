package com.ledgerflow.it;

import com.github.dockerjava.api.command.InspectContainerResponse;
import java.time.Duration;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.images.builder.Transferable;

/**
 * A single-node KRaft broker, configured by hand rather than through
 * Testcontainers' own KafkaContainer.
 *
 * The reason is a real incompatibility, not a preference: that class hardcodes
 * KAFKA_LISTENERS in the PLAINTEXT://0.0.0.0:9092 form, and apache/kafka 3.9
 * validates the broker config while formatting storage, where it rejects the
 * whole configuration with "advertised.listeners cannot use the nonroutable
 * meta-address 0.0.0.0". The container exits before Kafka ever starts.
 * Omitting the host entirely -- PLAINTEXT://:9092 -- binds every interface
 * just the same and is the form the image's own documentation uses. This
 * matches what docker-compose.yml configures, so the test broker and the local
 * broker are set up identically.
 *
 * The starter-script dance exists because of an ordering problem every
 * containerised Kafka has: clients must be told an address they can reach, but
 * the port is only assigned when the container starts, and Kafka reads its
 * advertised address at boot. So the container is given a command that waits
 * for a script, and the script is written once the mapped port is known.
 */
final class KRaftKafkaContainer extends GenericContainer<KRaftKafkaContainer> {

    private static final int KAFKA_PORT = 9092;
    private static final String STARTER_SCRIPT = "/tmp/start-kafka.sh";

    KRaftKafkaContainer(String image) {
        super(image);
        withExposedPorts(KAFKA_PORT);

        // Three listeners on one node: one for test clients, one the broker
        // uses to talk to itself as a broker, one for the Raft controller.
        withEnv("KAFKA_NODE_ID", "1");
        withEnv("KAFKA_PROCESS_ROLES", "broker,controller");
        withEnv("KAFKA_LISTENERS", "PLAINTEXT://:9092,BROKER://:9093,CONTROLLER://:9094");
        withEnv(
                "KAFKA_LISTENER_SECURITY_PROTOCOL_MAP",
                "PLAINTEXT:PLAINTEXT,BROKER:PLAINTEXT,CONTROLLER:PLAINTEXT");
        withEnv("KAFKA_INTER_BROKER_LISTENER_NAME", "BROKER");
        withEnv("KAFKA_CONTROLLER_LISTENER_NAMES", "CONTROLLER");
        withEnv("KAFKA_CONTROLLER_QUORUM_VOTERS", "1@localhost:9094");

        // One broker, so nothing can be replicated anywhere.
        withEnv("KAFKA_OFFSETS_TOPIC_REPLICATION_FACTOR", "1");
        withEnv("KAFKA_TRANSACTION_STATE_LOG_REPLICATION_FACTOR", "1");
        withEnv("KAFKA_TRANSACTION_STATE_LOG_MIN_ISR", "1");
        // Without this a consumer group waits three seconds to rebalance on
        // every single test.
        withEnv("KAFKA_GROUP_INITIAL_REBALANCE_DELAY_MS", "0");
        withEnv("CLUSTER_ID", "4L6g3nShT-eMCtK--X86sw");

        withCommand(
                "sh",
                "-c",
                "while [ ! -f " + STARTER_SCRIPT + " ]; do sleep 0.1; done; sh " + STARTER_SCRIPT);

        // Not forListeningPort: the port accepts connections well before the
        // broker will answer a metadata request, which produces a flaky suite
        // that fails on whichever test happens to run first.
        waitingFor(Wait.forLogMessage(".*Kafka Server started.*\\n", 1)
                .withStartupTimeout(Duration.ofMinutes(3)));
    }

    @Override
    protected void containerIsStarting(InspectContainerResponse containerInfo) {
        String advertised = "PLAINTEXT://%s:%d,BROKER://localhost:9093".formatted(getHost(), getMappedPort(KAFKA_PORT));
        copyFileToContainer(
                Transferable.of(
                        "#!/bin/sh\nexport KAFKA_ADVERTISED_LISTENERS=" + advertised
                                + "\nexec /etc/kafka/docker/run\n",
                        0755),
                STARTER_SCRIPT);
    }

    String getBootstrapServers() {
        return getHost() + ":" + getMappedPort(KAFKA_PORT);
    }
}
