package com.ledgerflow.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Pattern;
import java.time.LocalDate;
import java.util.List;

/**
 * @param txnDate the accounting date. Optional, defaulting to today, because
 *        most postings are for today -- but back-dating has to be expressible
 *        or the books cannot be corrected.
 * @param currency optional, defaulting to the organization's reporting
 *        currency. All lines share it: one journal entry, one currency.
 */
public record PostTransactionRequest(
        @NotBlank String idempotencyKey,
        String description,
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate txnDate,
        @Pattern(regexp = "[A-Za-z]{3}", message = "must be a three-letter currency code") String currency,
        @NotEmpty @Valid List<EntryRequest> entries) {}
