package com.ledgerflow.web.dto;

import com.ledgerflow.service.ColumnMapping;
import jakarta.validation.constraints.NotBlank;

public record ColumnMappingRequest(
        @NotBlank String dateColumn, @NotBlank String descriptionColumn, @NotBlank String amountColumn, String externalIdColumn) {

    public ColumnMapping toMapping() {
        return new ColumnMapping(dateColumn, descriptionColumn, amountColumn, externalIdColumn);
    }
}
