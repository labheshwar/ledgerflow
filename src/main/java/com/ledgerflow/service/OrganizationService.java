package com.ledgerflow.service;

import com.ledgerflow.domain.Organization;
import com.ledgerflow.repository.OrganizationRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

/**
 * The organization the current request is acting for, and the facts about it
 * that the rest of the application needs -- chiefly its reporting currency,
 * which every posting has to convert into.
 */
@Service
public class OrganizationService {

    private final OrganizationRepository organizationRepository;

    public OrganizationService(OrganizationRepository organizationRepository) {
        this.organizationRepository = organizationRepository;
    }

    public Organization current() {
        return byId(TenantContext.require());
    }

    public Organization byId(Long orgId) {
        return organizationRepository
                .findById(orgId)
                .orElseThrow(() -> new NoSuchElementException("No organization with id " + orgId));
    }

    /**
     * The currency every report is denominated in. Accounts may be held in
     * other currencies; the books are added up in this one.
     */
    public String baseCurrency() {
        return current().getBaseCurrency();
    }
}
