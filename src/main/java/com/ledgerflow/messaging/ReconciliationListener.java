package com.ledgerflow.messaging;

import com.ledgerflow.config.RabbitMQConfig;
import com.ledgerflow.service.ReconciliationService;
import com.ledgerflow.tenancy.TenantContext;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
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
