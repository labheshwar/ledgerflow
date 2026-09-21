package com.ledgerflow.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record EmailInvoiceRequest(@NotBlank @Email String recipientEmail) {}
