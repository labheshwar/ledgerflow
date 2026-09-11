package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.domain.TransactionStatus;
import java.time.OffsetDateTime;
import java.util.List;

public record TransactionDetailResponse(
        Long id,
        String idempotencyKey,
        String description,
        TransactionStatus status,
        OffsetDateTime createdAt,
        List<TransactionEntryResponse> entries) {

    public static TransactionDetailResponse from(Transaction transaction, List<Entry> entries) {
        return new TransactionDetailResponse(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                transaction.getDescription(),
                transaction.getStatus(),
                transaction.getCreatedAt(),
                entries.stream().map(TransactionEntryResponse::from).toList());
    }
}
