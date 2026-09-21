package com.ledgerflow.service;

import java.time.LocalDate;
import java.util.List;

/**
 * The editable shape of an invoice, used for both create and update.
 *
 * {@code currency} is null for the ordinary case, in which the invoice is
 * denominated in the organization's own base currency exactly as every
 * invoice was before milestone 15.
 */
public record InvoiceDraft(
        Long contactId, LocalDate issueDate, LocalDate dueDate, String notes, List<InvoiceLineDraft> lines, String currency) {

    public InvoiceDraft(Long contactId, LocalDate issueDate, LocalDate dueDate, String notes, List<InvoiceLineDraft> lines) {
        this(contactId, issueDate, dueDate, notes, lines, null);
    }
}
