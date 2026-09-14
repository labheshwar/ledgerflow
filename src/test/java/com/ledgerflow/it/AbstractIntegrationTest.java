package com.ledgerflow.it;

import com.ledgerflow.tenancy.TenantContext;
import java.time.Duration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
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
 * any real embedded server startup.
 *
 * The application connects as ledgerflow_app, exactly as it does in
 * production, while Flyway connects as the container's superuser to create
 * the schema. That split matters: a superuser bypasses row-level security,
 * so running the suite as one would mean the isolation policies were never
 * actually exercised by any of these tests.
 *
 * Tests must not assume specific IDs/state from another test class ran
 * first -- create whatever accounts/data a test needs itself.
 */
@SpringBootTest
public abstract class AbstractIntegrationTest {

    /** Seeded by V9; the org every pre-existing row was migrated into. */
    protected static final Long DEMO_ORG_ID = 1L;

    private static final String APP_PASSWORD = "test-app-password";

    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("ledgerflow")
            .withUsername("ledgerflow")
            .withPassword("ledgerflow")
            .withStartupTimeout(Duration.ofMinutes(2));

    /**
     * RabbitMQ's broker plus management plugin routinely takes over two
     * minutes to log "Server startup complete" on a constrained or
     * docker-in-docker host, well past Testcontainers' 60s default. Blowing
     * that deadline fails this static initializer, which then cascades as
     * NoClassDefFoundError across every integration test in the run.
     */
    static final RabbitMQContainer RABBITMQ = new RabbitMQContainer("rabbitmq:3-management-alpine")
            .withStartupTimeout(Duration.ofMinutes(5));

    static final GenericContainer<?> REDIS = new GenericContainer<>("redis:7-alpine")
            .withExposedPorts(6379)
            .withStartupTimeout(Duration.ofMinutes(2));

    static {
        POSTGRES.start();
        RABBITMQ.start();
        REDIS.start();
    }

    @DynamicPropertySource
    static void registerProperties(DynamicPropertyRegistry registry) {
        // Runtime identity: created by V10, no BYPASSRLS.
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> "ledgerflow_app");
        registry.add("spring.datasource.password", () -> APP_PASSWORD);
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "validate");

        // Migration identity: owns the schema and creates the role above.
        registry.add("spring.flyway.enabled", () -> "true");
        registry.add("spring.flyway.url", POSTGRES::getJdbcUrl);
        registry.add("spring.flyway.user", POSTGRES::getUsername);
        registry.add("spring.flyway.password", POSTGRES::getPassword);
        registry.add("spring.flyway.placeholders.app_password", () -> APP_PASSWORD);

        registry.add("spring.data.redis.host", REDIS::getHost);
        registry.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));

        registry.add("spring.rabbitmq.host", RABBITMQ::getHost);
        registry.add("spring.rabbitmq.port", RABBITMQ::getAmqpPort);
        registry.add("spring.rabbitmq.username", RABBITMQ::getAdminUsername);
        registry.add("spring.rabbitmq.password", RABBITMQ::getAdminPassword);
    }

    /**
     * Without a tenant every query is filtered to nothing, so tests would
     * fail in a thoroughly confusing way -- correct SQL returning no rows.
     */
    @BeforeEach
    void establishTenant() {
        TenantContext.set(DEMO_ORG_ID);
    }

    @AfterEach
    void clearTenant() {
        TenantContext.clear();
    }

    /**
     * A connection as the owning superuser, which bypasses row-level
     * security. Tests asserting on database-level machinery -- triggers,
     * constraints, another organization's rows -- need to see past the
     * policies that the application deliberately cannot.
     */
    protected static JdbcTemplate ownerJdbc() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword());
        dataSource.setDriverClassName("org.postgresql.Driver");
        return new JdbcTemplate(dataSource);
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
