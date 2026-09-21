package com.ledgerflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;

/**
 * Resolves an unguessable token to the organization and invoice it means,
 * for an anonymous request that by definition has no tenant yet. See V18's
 * migration comment for why this table carries no row-level security policy
 * at all -- the token itself is the access control.
 */
@Entity
@Table(name = "invoice_public_links")
public class InvoicePublicLink {

    @Id
    private String token;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public String getToken() {
        return token;
    }

    public void setToken(String token) {
        this.token = token;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public void setInvoiceId(Long invoiceId) {
        this.invoiceId = invoiceId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
