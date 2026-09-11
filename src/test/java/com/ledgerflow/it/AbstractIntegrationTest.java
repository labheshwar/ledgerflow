package com.ledgerflow.it;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.containers.RabbitMQContainer;

/**
 * Real Postgres, Redis, and RabbitMQ, started once and shared across every
 * subclass -- deliberately NOT using @Testcontainers/@Container, whose
 * per-class lifecycle hooks were observed recreating fresh containers (new
 * random ports) for each test class even with static fields declared here,
 * leaving a cached Spring context holding a DataSource pointed at a now-dead
 * port. A manual static initializer, started exactly once for the whole JVM
 * and left running for Ryuk to reap at shutdown, is the documented
 * Testcontainers pattern for cross-class sharing.
 *
 * WebEnvironment defaults to MOCK: the full web/security auto-configuration
 * wires up without starting a real embedded Tomcat -- none of these tests
 * make an actual HTTP call, and this host's JDK has a NIO bug that breaks
 * any real embedded server startup (see the Milestone 9 commit notes for
 * the same issue hit running the app directly on this machine).
 *
 * Tests must not assume specific IDs/state from another test class ran
 * first -- create whatever accounts/data a test needs itself.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ledgerflow")
            .withUsername("ledgerflow")
            .withPassword("ledgerflow");

    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3-management-alpine");

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine").withExposedPorts(6379);

    static {
        POSTGRES.start();
        RABBITMQ.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");
        registry.add("spring.flyway.enabled", () -> "true");

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    /**
     * Polls a condition until it's true or the timeout elapses. Small and
     * bespoke rather than pulling in Awaitility for one use.
     */
    protected static void awaitCondition(java.util.function.Supplier<Boolean> condition) {
        for (int i = 0; i < 40; i++) {
            if (Boolean.TRUE.equals(condition.get())) {
                return;
            }
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for condition", e);
            }
        }
        throw new AssertionError("Condition not met within 10s");
    }
}
