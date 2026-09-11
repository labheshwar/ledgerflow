package com.ledgerflow.messaging;

import com.ledgerflow.config.RabbitMQConfig;
import com.ledgerflow.domain.ReconciliationBatch;
import com.ledgerflow.domain.ReconciliationStatus;
import com.ledgerflow.repository.ReconciliationBatchRepository;
import java.time.OffsetDateTime;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.retry.MessageRecoverer;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.stereotype.Component;

/**
 * Invoked once the listener's in-process retries are exhausted for a given
 * message. Marks the batch FAILED before handing off to a
 * RepublishMessageRecoverer, which moves the message to the dead-letter
 * queue instead of it being lost or retried forever.
 */
@Component
public class ReconciliationFailureRecoverer implements MessageRecoverer {

    private final ReconciliationBatchRepository batchRepository;
    private final Jackson2JsonMessageConverter converter;
    private final RepublishMessageRecoverer delegate;

    public ReconciliationFailureRecoverer(
            RabbitTemplate rabbitTemplate,
            ReconciliationBatchRepository batchRepository,
            Jackson2JsonMessageConverter converter) {
        this.batchRepository = batchRepository;
        this.converter = converter;
        this.delegate = new RepublishMessageRecoverer(rabbitTemplate, RabbitMQConfig.DLX, RabbitMQConfig.DLQ_ROUTING_KEY);
    }

    @Override
    public void recover(Message message, Throwable cause) {
        try {
            ReconciliationRequestedEvent event = (ReconciliationRequestedEvent) converter.fromMessage(message);
            batchRepository.findById(event.batchId()).ifPresent(batch -> markFailed(batch));
        } finally {
            delegate.recover(message, cause);
        }
    }

    private void markFailed(ReconciliationBatch batch) {
        batch.setStatus(ReconciliationStatus.FAILED);
        batch.setCompletedAt(OffsetDateTime.now());
        batchRepository.save(batch);
    }
}
