package com.ledgerflow.messaging;

import com.ledgerflow.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class InvoiceEmailProducer {

    private final RabbitTemplate rabbitTemplate;

    public InvoiceEmailProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(Long orgId, Long invoiceId, String recipientEmail, boolean reminder) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.INVOICE_EMAIL_EXCHANGE,
                RabbitMQConfig.INVOICE_EMAIL_ROUTING_KEY,
                new InvoiceEmailRequestedEvent(orgId, invoiceId, recipientEmail, reminder));
    }
}
