package com.ledgerflow.web.dto;

import com.ledgerflow.service.BillDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record BillRequest(
        @NotNull Long contactId,
        @NotBlank @Size(max = 50) String vendorReference,
        @NotNull LocalDate billDate,
        @NotNull LocalDate dueDate,
        @Size(max = 1000) String notes,
        @NotEmpty @Valid List<BillLineRequest> lines) {

    public BillDraft toDraft() {
        return new BillDraft(
                contactId, vendorReference, billDate, dueDate, notes, lines.stream().map(BillLineRequest::toDraft).toList());
    }
}
