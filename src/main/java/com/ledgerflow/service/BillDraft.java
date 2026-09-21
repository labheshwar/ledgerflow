package com.ledgerflow.service;

import java.time.LocalDate;
import java.util.List;

/** The editable shape of a bill, used for both create and update. */
public record BillDraft(
        Long contactId,
        String vendorReference,
        LocalDate billDate,
        LocalDate dueDate,
        String notes,
        List<BillLineDraft> lines) {}
