package com.ledgerflow.web.dto;

import com.ledgerflow.domain.StatementImport;
import com.ledgerflow.domain.StatementImportStatus;
import java.time.OffsetDateTime;

public record StatementImportResponse(
        Long id,
        Long bankAccountId,
        StatementImportStatus status,
        String originalFilename,
        String dateColumn,
        String descriptionColumn,
        String amountColumn,
        String externalIdColumn,
        int totalRows,
        int processedRows,
        int newRows,
        int duplicateRows,
        int errorRows,
        String errorMessage,
        OffsetDateTime createdAt,
        OffsetDateTime completedAt) {

    public static StatementImportResponse from(StatementImport statementImport) {
        return new StatementImportResponse(
                statementImport.getId(),
                statementImport.getBankAccountId(),
                statementImport.getStatus(),
                statementImport.getOriginalFilename(),
                statementImport.getDateColumn(),
                statementImport.getDescriptionColumn(),
                statementImport.getAmountColumn(),
                statementImport.getExternalIdColumn(),
                statementImport.getTotalRows(),
                statementImport.getProcessedRows(),
                statementImport.getNewRows(),
                statementImport.getDuplicateRows(),
                statementImport.getErrorRows(),
                statementImport.getErrorMessage(),
                statementImport.getCreatedAt(),
                statementImport.getCompletedAt());
    }
}
