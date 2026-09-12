package com.ledgerflow.repository;

import com.ledgerflow.domain.ReconciliationResultStatus;

/** One row of the grouped count behind the reconciliation list. */
public record BatchStatusCount(Long batchId, ReconciliationResultStatus status, Long count) {}
