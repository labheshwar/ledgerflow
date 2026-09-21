package com.ledgerflow.web.dto;

import com.ledgerflow.service.InvoiceDraft;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record InvoiceRequest(
        @NotNull Long contactId,
        @NotNull LocalDate issueDate,
        @NotNull LocalDate dueDate,
        @Size(max = 1000) String notes,
        @NotEmpty @Valid List<InvoiceLineRequest> lines,
        String currency) {

    public InvoiceDraft toDraft() {
        return new InvoiceDraft(
                contactId, issueDate, dueDate, notes, lines.stream().map(InvoiceLineRequest::toDraft).toList(), currency);
    }
}
