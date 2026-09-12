package com.ledgerflow.web;

import com.ledgerflow.domain.ReconciliationBatch;
import com.ledgerflow.domain.ReconciliationResultStatus;
import com.ledgerflow.domain.ReconciliationStatus;
import com.ledgerflow.messaging.ReconciliationProducer;
import com.ledgerflow.repository.BatchStatusCount;
import com.ledgerflow.repository.ReconciliationBatchRepository;
import com.ledgerflow.repository.ReconciliationResultRepository;
import com.ledgerflow.web.dto.PagedResponse;
import com.ledgerflow.web.dto.ReconciliationBatchResponse;
import com.ledgerflow.web.dto.ReconciliationBatchSummaryResponse;
import com.ledgerflow.web.dto.ReconciliationResultResponse;
import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;
import java.util.Set;
import java.util.stream.Collectors;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/reconciliation")
public class ReconciliationController {

    private static final Set<String> SORTABLE = Set.of("triggeredAt", "completedAt", "status");

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
    public PagedResponse<ReconciliationBatchSummaryResponse> listBatches(
            @RequestParam(required = false) ReconciliationStatus status,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {

        Pageable sorted = SortWhitelist.apply(pageable, SORTABLE, Sort.by("triggeredAt").descending());
        Page<ReconciliationBatch> batches = batchRepository.search(status, sorted);

        List<Long> batchIds = batches.getContent().stream().map(ReconciliationBatch::getId).toList();
        Map<Long, Map<ReconciliationResultStatus, Long>> tallies = batchIds.isEmpty()
                ? Map.of()
                : resultRepository.countByStatusForBatches(batchIds).stream()
                        .collect(Collectors.groupingBy(
                                BatchStatusCount::batchId,
                                Collectors.toMap(BatchStatusCount::status, BatchStatusCount::count)));

        List<ReconciliationBatchSummaryResponse> content = batches.getContent().stream()
                .map(batch -> {
                    Map<ReconciliationResultStatus, Long> byStatus =
                            tallies.getOrDefault(batch.getId(), Map.of());
                    return ReconciliationBatchSummaryResponse.from(
                            batch,
                            byStatus.getOrDefault(ReconciliationResultStatus.MATCHED, 0L),
                            byStatus.getOrDefault(ReconciliationResultStatus.MISMATCHED, 0L));
                })
                .toList();

        return PagedResponse.of(content, batches);
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
