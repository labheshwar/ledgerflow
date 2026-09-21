package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.messaging.StatementImportListener;
import org.junit.jupiter.api.Test;
import org.springframework.amqp.rabbit.listener.RabbitListenerEndpointRegistry;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;

/**
 * Asserts the split is real.
 *
 * Without this, web and worker are two strings that happen to be handed to
 * different containers. An annotation accidentally left off a listener would
 * mean every web replica quietly competes for queued jobs -- work done twice,
 * no error raised, and nothing visible until there is more than one replica.
 *
 * inheritProfiles = false because @ActiveProfiles merges with the parent's by
 * default, which would activate worker here and test nothing.
 */
@ActiveProfiles(value = "web", inheritProfiles = false)
class WebProfileIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ApplicationContext context;

    @Autowired
    private RabbitListenerEndpointRegistry listenerRegistry;

    @Test
    void theWebProfileConsumesNothingAndPollsNothing() {
        assertThat(listenerRegistry.getListenerContainers())
                .as("a web replica must not compete with workers for queued jobs")
                .isEmpty();

        assertThat(context.getBeanNamesForType(StatementImportListener.class)).isEmpty();

        // No poller, and therefore no connection pool holding the one
        // identity in the system that can read across organizations.
        assertThat(context.containsBean("outboxStore")).isFalse();
    }

    @Test
    void theWebProfileStillDeclaresTheTopologyItPublishesTo() {
        // Topology beans are deliberately not profiled. If they were, a stack
        // brought up before any worker existed would have the web tier
        // publishing to an exchange that had never been declared, and the
        // broker would drop those messages without complaint.
        assertThat(context.containsBean("statementImportExchange")).isTrue();
        assertThat(context.containsBean("statementImportQueue")).isTrue();
        assertThat(context.containsBean("statementImportBinding")).isTrue();
    }
}
