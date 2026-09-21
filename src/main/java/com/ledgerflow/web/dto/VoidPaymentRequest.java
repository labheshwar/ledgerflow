package com.ledgerflow.web.dto;

import jakarta.validation.constraints.Size;

public record VoidPaymentRequest(@Size(max = 255) String reason) {}
