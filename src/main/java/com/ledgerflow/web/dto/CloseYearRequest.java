package com.ledgerflow.web.dto;

import com.fasterxml.jackson.annotation.JsonFormat;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;

/** @param asOfDate the last day of the fiscal year being closed, e.g. 2026-12-31. */
public record CloseYearRequest(
        @NotNull @JsonFormat(shape = JsonFormat.Shape.STRING, pattern = "yyyy-MM-dd") LocalDate asOfDate) {}
