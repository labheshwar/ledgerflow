package com.ledgerflow.service;

import java.time.LocalDate;
import java.util.List;

/** The editable shape of an invoice, used for both create and update. */
public record InvoiceDraft(
        Long contactId, LocalDate issueDate, LocalDate dueDate, String notes, List<InvoiceLineDraft> lines) {}
