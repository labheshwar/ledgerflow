package com.ledgerflow.web.dto;

import com.ledgerflow.service.BankAccountDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record BankAccountRequest(
        @NotNull Long accountId, @NotBlank @Size(max = 255) String name, @Size(max = 4) String accountNumberLast4) {

    public BankAccountDraft toDraft() {
        return new BankAccountDraft(accountId, name, accountNumberLast4);
    }
}
