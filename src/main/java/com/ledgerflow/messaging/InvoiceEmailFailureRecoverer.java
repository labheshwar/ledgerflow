package com.ledgerflow.messaging;

import com.ledgerflow.config.RabbitMQConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.stereotype.Component;

/**
 * Invoked once in-process retries for a stuck email are exhausted. Unlike
 * {@link StatementImportFailureRecoverer} there is no row of its own to
 * mark FAILED -- an email send has no status of its own to update, only
 * the DLQ as the record that it never went out.
 */
@Component
public class InvoiceEmailFailureRecoverer implements MessageRecoverer {

    private static final Logger log = LoggerFactory.getLogger(InvoiceEmailFailureRecoverer.class);

    private final RepublishMessageRecoverer delegate;

    public InvoiceEmailFailureRecoverer(RabbitTemplate rabbitTemplate) {
        this.delegate = new RepublishMessageRecoverer(
                rabbitTemplate, RabbitMQConfig.INVOICE_EMAIL_DLX, RabbitMQConfig.INVOICE_EMAIL_DLQ_ROUTING_KEY);
    }

    @Override
    public void recover(Message message, Throwable cause) {
        log.error("Giving up sending an invoice email after retries; moved to the dead-letter queue", cause);
        delegate.recover(message, cause);
    }
}
