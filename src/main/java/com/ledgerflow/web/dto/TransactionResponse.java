package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Transaction;
import com.ledgerflow.domain.TransactionStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TransactionResponse(
        Long id,
        String idempotencyKey,
        String description,
        TransactionStatus status,
        LocalDate txnDate,
        /** The transaction this one undoes, or null for an ordinary posting. */
        Long reversalOfTransactionId,
        OffsetDateTime createdAt) {

    public static TransactionResponse from(Transaction transaction) {
        return new TransactionResponse(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                transaction.getDescription(),
                transaction.getStatus(),
                transaction.getTxnDate(),
                transaction.getReversalOfTransactionId(),
                transaction.getCreatedAt());
    }
}
