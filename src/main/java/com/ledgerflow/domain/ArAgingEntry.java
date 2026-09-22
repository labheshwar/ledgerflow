package com.ledgerflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One still-open invoice, as last recomputed by the AR aging projector.
 * Composite-keyed on (orgId, invoiceId) rather than a surrogate id -- a
 * rebuild upserts this exact row, it never needs to be looked up any other
 * way.
 */
@Entity
@Table(name = "ar_aging")
@IdClass(AgingEntryId.class)
public class ArAgingEntry {

    @jakarta.persistence.Id
    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @jakarta.persistence.Id
    @Column(name = "invoice_id", nullable = false)
    private Long invoiceId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "invoice_number")
    private String invoiceNumber;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal balance;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 12)
    private AgingBucket bucket;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public ArAgingEntry() {}

    public ArAgingEntry(
            Long orgId,
            Long invoiceId,
            Long contactId,
            String contactName,
            String invoiceNumber,
            LocalDate dueDate,
            String currency,
            BigDecimal balance,
            AgingBucket bucket) {
        this.orgId = orgId;
        this.invoiceId = invoiceId;
        this.contactId = contactId;
        this.contactName = contactName;
        this.invoiceNumber = invoiceNumber;
        this.dueDate = dueDate;
        this.currency = currency;
        this.balance = balance;
        this.bucket = bucket;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getOrgId() {
        return orgId;
    }

    public Long getInvoiceId() {
        return invoiceId;
    }

    public Long getContactId() {
        return contactId;
    }

    public String getContactName() {
        return contactName;
    }

    public String getInvoiceNumber() {
        return invoiceNumber;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public String getCurrency() {
        return currency;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public AgingBucket getBucket() {
        return bucket;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
