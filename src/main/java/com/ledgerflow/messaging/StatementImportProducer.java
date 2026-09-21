package com.ledgerflow.messaging;

import com.ledgerflow.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class StatementImportProducer {

    private final RabbitTemplate rabbitTemplate;

    public StatementImportProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(Long orgId, Long importId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.STATEMENT_IMPORT_EXCHANGE,
                RabbitMQConfig.STATEMENT_IMPORT_ROUTING_KEY,
                new StatementImportRequestedEvent(orgId, importId));
    }
}
