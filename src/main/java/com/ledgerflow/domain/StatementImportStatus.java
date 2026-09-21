package com.ledgerflow.domain;

/**
 * UPLOADED is a raw file with no mapping yet. Submitting a column mapping
 * moves it to PROCESSING and queues the worker job; PREVIEWED is what the
 * user reviews before choosing to COMMIT. FAILED is terminal, same as a
 * reconciliation batch -- re-import rather than retry in place, since the
 * mapping itself might be what was wrong.
 */
public enum StatementImportStatus {
    UPLOADED,
    PROCESSING,
    PREVIEWED,
    COMMITTED,
    FAILED
}
