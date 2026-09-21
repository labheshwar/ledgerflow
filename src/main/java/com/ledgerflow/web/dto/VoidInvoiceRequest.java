package com.ledgerflow.web.dto;

import jakarta.validation.constraints.Size;

public record VoidInvoiceRequest(@Size(max = 255) String reason) {}
