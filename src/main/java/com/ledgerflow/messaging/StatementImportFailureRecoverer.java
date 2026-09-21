package com.ledgerflow.messaging;

import com.ledgerflow.config.RabbitMQConfig;
import com.ledgerflow.domain.StatementImport;
import com.ledgerflow.domain.StatementImportStatus;
import com.ledgerflow.repository.StatementImportRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.time.OffsetDateTime;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Component;

/** Mirrors {@link ReconciliationFailureRecoverer} exactly, for the same reason. */
@Component
public class StatementImportFailureRecoverer implements MessageRecoverer {

    private final StatementImportRepository importRepository;
    private final Jackson2JsonMessageConverter converter;
    private final RepublishMessageRecoverer delegate;

    public StatementImportFailureRecoverer(
            RabbitTemplate rabbitTemplate, StatementImportRepository importRepository, Jackson2JsonMessageConverter converter) {
        this.importRepository = importRepository;
        this.converter = converter;
        this.delegate = new RepublishMessageRecoverer(
                rabbitTemplate, RabbitMQConfig.STATEMENT_IMPORT_DLX, RabbitMQConfig.STATEMENT_IMPORT_DLQ_ROUTING_KEY);
    }

    @Override
    public void recover(Message message, Throwable cause) {
        try {
            StatementImportRequestedEvent event = (StatementImportRequestedEvent) converter.fromMessage(message);
            TenantContext.runAs(
                    event.orgId(), () -> importRepository.findById(event.importId()).ifPresent(imp -> markFailed(imp, cause)));
        } finally {
            delegate.recover(message, cause);
        }
    }

    private void markFailed(StatementImport statementImport, Throwable cause) {
        statementImport.setStatus(StatementImportStatus.FAILED);
        statementImport.setErrorMessage(cause.getMessage());
        statementImport.setCompletedAt(OffsetDateTime.now());
        importRepository.save(statementImport);
    }
}
