package com.ledgerflow.web.dto;

import jakarta.validation.constraints.Size;

public record VoidBillRequest(@Size(max = 255) String reason) {}
