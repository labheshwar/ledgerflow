package com.ledgerflow.web.dto;

import com.ledgerflow.domain.ContactType;
import com.ledgerflow.service.ContactDraft;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record ContactRequest(
        @NotNull ContactType type,
        @NotBlank @Size(max = 255) String name,
        @Email @Size(max = 255) String email,
        @Size(max = 50) String phone,
        @Size(max = 50) String taxId,
        @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @Size(max = 100) String city,
        @Size(max = 100) String state,
        @Size(max = 20) String postalCode,
        @Size(max = 100) String country,
        @Size(max = 1000) String notes) {

    public ContactDraft toDraft() {
        return new ContactDraft(
                type, name, email, phone, taxId, addressLine1, addressLine2, city, state, postalCode, country,
                notes);
    }
}
