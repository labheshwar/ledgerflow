package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;

import com.ledgerflow.domain.ReconciliationBatch;
import com.ledgerflow.domain.ReconciliationStatus;
import com.ledgerflow.messaging.ReconciliationProducer;
import com.ledgerflow.repository.ReconciliationBatchRepository;
import com.ledgerflow.repository.ReconciliationResultRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * Exercises the real publish -> broker -> listener -> service round trip
 * over an actual RabbitMQ instance, rather than calling the listener method
 * directly in-process.
 */
class ReconciliationIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ReconciliationBatchRepository batchRepository;

    @Autowired
    private ReconciliationResultRepository resultRepository;

    @Autowired
    private ReconciliationProducer producer;

    @Test
    void triggeringReconciliationCompletesAsynchronouslyOverRealRabbitMq() {
        ReconciliationBatch batch = new ReconciliationBatch();
        batch.setStatus(ReconciliationStatus.PENDING);
        batch = batchRepository.save(batch);
        Long batchId = batch.getId();

        producer.publish(batchId);

        awaitCondition(() -> batchRepository.findById(batchId)
                .map(b -> b.getStatus() == ReconciliationStatus.COMPLETED)
                .orElse(false));

        ReconciliationBatch completed = batchRepository.findById(batchId).orElseThrow();
        assertThat(completed.getStatus()).isEqualTo(ReconciliationStatus.COMPLETED);
        assertThat(completed.getCompletedAt()).isNotNull();
        assertThat(resultRepository.findByBatchId(batchId)).isNotEmpty();
    }
}
