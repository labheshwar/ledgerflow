package com.ledgerflow.messaging;

import com.ledgerflow.config.RabbitMQConfig;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

@Component
public class ReconciliationProducer {

    private final RabbitTemplate rabbitTemplate;

    public ReconciliationProducer(RabbitTemplate rabbitTemplate) {
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(Long batchId) {
        rabbitTemplate.convertAndSend(
                RabbitMQConfig.EXCHANGE, RabbitMQConfig.ROUTING_KEY, new ReconciliationRequestedEvent(batchId));
    }
}
