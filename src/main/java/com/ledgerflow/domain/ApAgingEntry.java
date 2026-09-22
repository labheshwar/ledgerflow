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

/** One still-open bill, as last recomputed by the AP aging projector -- the mirror of {@link ArAgingEntry}. */
@Entity
@Table(name = "ap_aging")
@IdClass(ApAgingEntryId.class)
public class ApAgingEntry {

    @jakarta.persistence.Id
    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @jakarta.persistence.Id
    @Column(name = "bill_id", nullable = false)
    private Long billId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Column(name = "contact_name", nullable = false)
    private String contactName;

    @Column(name = "bill_number")
    private String billNumber;

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

    public ApAgingEntry() {}

    public ApAgingEntry(
            Long orgId,
            Long billId,
            Long contactId,
            String contactName,
            String billNumber,
            LocalDate dueDate,
            String currency,
            BigDecimal balance,
            AgingBucket bucket) {
        this.orgId = orgId;
        this.billId = billId;
        this.contactId = contactId;
        this.contactName = contactName;
        this.billNumber = billNumber;
        this.dueDate = dueDate;
        this.currency = currency;
        this.balance = balance;
        this.bucket = bucket;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getOrgId() {
        return orgId;
    }

    public Long getBillId() {
        return billId;
    }

    public Long getContactId() {
        return contactId;
    }

    public String getContactName() {
        return contactName;
    }

    public String getBillNumber() {
        return billNumber;
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
