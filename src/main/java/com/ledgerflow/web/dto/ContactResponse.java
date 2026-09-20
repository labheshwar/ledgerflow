package com.ledgerflow.web.dto;

import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.ContactType;
import java.time.OffsetDateTime;

public record ContactResponse(
        Long id,
        ContactType type,
        String name,
        String email,
        String phone,
        String taxId,
        String addressLine1,
        String addressLine2,
        String city,
        String state,
        String postalCode,
        String country,
        String notes,
        boolean archived,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt) {

    public static ContactResponse from(Contact c) {
        return new ContactResponse(
                c.getId(),
                c.getType(),
                c.getName(),
                c.getEmail(),
                c.getPhone(),
                c.getTaxId(),
                c.getAddressLine1(),
                c.getAddressLine2(),
                c.getCity(),
                c.getState(),
                c.getPostalCode(),
                c.getCountry(),
                c.getNotes(),
                c.isArchived(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
