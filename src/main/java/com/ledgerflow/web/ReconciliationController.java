package com.ledgerflow.web;

import com.ledgerflow.domain.ReconciliationBatch;
import com.ledgerflow.domain.ReconciliationStatus;
import com.ledgerflow.messaging.ReconciliationProducer;
import com.ledgerflow.repository.ReconciliationBatchRepository;
import com.ledgerflow.repository.ReconciliationResultRepository;
import com.ledgerflow.web.dto.ReconciliationBatchResponse;
import com.ledgerflow.web.dto.ReconciliationResultResponse;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reconciliation")
public class ReconciliationController {

    private final ReconciliationBatchRepository batchRepository;
    private final ReconciliationResultRepository resultRepository;
    private final ReconciliationProducer producer;

    public ReconciliationController(
            ReconciliationBatchRepository batchRepository,
            ReconciliationResultRepository resultRepository,
            ReconciliationProducer producer) {
        this.batchRepository = batchRepository;
        this.resultRepository = resultRepository;
        this.producer = producer;
    }

    @GetMapping
    public List<ReconciliationBatchResponse> listBatches() {
        return batchRepository.findAllByOrderByTriggeredAtDesc().stream()
                .map(batch -> ReconciliationBatchResponse.from(
                        batch,
                        resultRepository.findByBatchId(batch.getId()).stream()
                                .map(ReconciliationResultResponse::from)
                                .toList()))
                .toList();
    }

    @PostMapping("/trigger")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ReconciliationBatchResponse trigger() {
        ReconciliationBatch batch = new ReconciliationBatch();
        batch.setStatus(ReconciliationStatus.PENDING);
        batch = batchRepository.save(batch);

        producer.publish(batch.getId());

        return ReconciliationBatchResponse.from(batch, List.of());
    }

    @GetMapping("/{id}")
    public ReconciliationBatchResponse getBatch(@PathVariable Long id) {
        ReconciliationBatch batch = batchRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No reconciliation batch with id " + id));

        var results = resultRepository.findByBatchId(id).stream()
                .map(ReconciliationResultResponse::from)
                .toList();

        return ReconciliationBatchResponse.from(batch, results);
    }
}
