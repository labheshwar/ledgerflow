package com.ledgerflow.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import java.math.BigDecimal;
import java.time.LocalDate;

public record FxRateRequest(@NotBlank String currency, @NotNull @Positive BigDecimal rate, @NotNull LocalDate asOfDate) {}
