package com.ledgerflow.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;

/**
 * @param reversalDate defaults to today when omitted -- reversing is
 *        routinely done on a different day than the mistake it corrects
 * @param reason optional; appended to the reversal's own description
 */
public record ReverseTransactionRequest(
        @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate reversalDate,
        @Size(max = 255) String reason) {}
