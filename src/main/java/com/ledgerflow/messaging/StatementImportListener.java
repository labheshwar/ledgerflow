package com.ledgerflow.messaging;

import com.ledgerflow.config.RabbitMQConfig;
import com.ledgerflow.service.StatementImportService;
import com.ledgerflow.tenancy.TenantContext;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Worker-only, same reasoning as {@link InvoiceEmailListener}. */
@Component
@Profile("worker")
public class StatementImportListener {

    private final StatementImportService statementImportService;

    public StatementImportListener(StatementImportService statementImportService) {
        this.statementImportService = statementImportService;
    }

    @RabbitListener(queues = RabbitMQConfig.STATEMENT_IMPORT_QUEUE, containerFactory = "statementImportListenerContainerFactory")
    public void onStatementImportRequested(StatementImportRequestedEvent event) {
        TenantContext.runAs(event.orgId(), () -> statementImportService.processPreview(event.importId()));
    }
}
