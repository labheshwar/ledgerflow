package com.ledgerflow.service;

import com.ledgerflow.domain.ContactType;

/** The editable shape of a contact, used for both create and update. */
public record ContactDraft(
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
        String notes) {}
