package com.ledgerflow.web.dto;

import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.service.AccountDraft;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * @param currency null means the organization's reporting currency, which is
 *        what nearly every account is held in
 * @param postable defaults true; false makes this a heading
 */
public record AccountRequest(
        @NotBlank @Size(max = 20) String code,
        @NotBlank @Size(max = 255) String name,
        @Size(max = 500) String description,
        @NotNull AccountType type,
        @Pattern(regexp = "[A-Za-z]{3}", message = "must be a three-letter currency code") String currency,
        Long parentId,
        SystemAccountRole systemRole,
        Boolean postable) {

    public AccountDraft toDraft() {
        return new AccountDraft(
                code, name, description, type, currency, parentId, systemRole, postable == null || postable);
    }
}
