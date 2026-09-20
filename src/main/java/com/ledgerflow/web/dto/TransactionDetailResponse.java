package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.domain.TransactionStatus;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record TransactionDetailResponse(
        Long id,
        String idempotencyKey,
        String description,
        TransactionStatus status,
        LocalDate txnDate,
        /** The transaction this one undoes, or null for an ordinary posting. */
        Long reversalOfTransactionId,
        /**
         * The transaction that undoes this one, or null if it never has
         * been. Derived by looking for a row pointing back here -- see
         * Transaction.reversalOfTransactionId -- not stored on this row.
         */
        Long reversedByTransactionId,
        OffsetDateTime createdAt,
        List<TransactionEntryResponse> entries) {

    public static TransactionDetailResponse from(
            Transaction transaction, List<Entry> entries, Long reversedByTransactionId) {
        return new TransactionDetailResponse(
                transaction.getId(),
                transaction.getIdempotencyKey(),
                transaction.getDescription(),
                transaction.getStatus(),
                transaction.getTxnDate(),
                transaction.getReversalOfTransactionId(),
                reversedByTransactionId,
                transaction.getCreatedAt(),
                entries.stream().map(TransactionEntryResponse::from).toList());
    }
}
