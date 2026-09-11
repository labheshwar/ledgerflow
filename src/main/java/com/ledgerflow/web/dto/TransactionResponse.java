package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Transaction;
import com.ledgerflow.domain.TransactionStatus;
import java.time.OffsetDateTime;

public record TransactionResponse(
        Long id,
        String idempotencyKey,
        String description,
        TransactionStatus status,
        OffsetDateTime createdAt) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                transaction.getDescription(),
                transaction.getStatus(),
                transaction.getCreatedAt());
    }
}
