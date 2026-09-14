package com.ledgerflow.messaging;

import com.ledgerflow.config.RabbitMQConfig;
import com.ledgerflow.service.ReconciliationService;
import com.ledgerflow.tenancy.TenantContext;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Worker-only. The profile split is what keeps a web replica from spending
 * request threads on background work -- and, in the other direction, lets
 * workers be scaled on queue depth without also scaling the HTTP tier.
 *
 * Note the exchange, queue and binding beans in RabbitMQConfig are
 * deliberately NOT profiled: the web process has to be able to publish to a
 * topology that exists, whether or not a worker has ever started.
 */
@Component
@Profile("worker")
public class ReconciliationListener {

    private final ReconciliationService reconciliationService;

    public ReconciliationListener(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @RabbitListener(queues = RabbitMQConfig.QUEUE)
    public void onReconciliationRequested(ReconciliationRequestedEvent event) {
        TenantContext.runAs(event.orgId(), () -> reconciliationService.reconcile(event.batchId()));
    }
}
