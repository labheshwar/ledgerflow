package com.ledgerflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A header only -- its lines live in {@link BillLine}, joined by
 * {@code billId} rather than a JPA collection, for the same reason
 * {@link Invoice} joins {@link InvoiceLine} that way.
 *
 * A bill carries two numbers that mean different things: {@link #billNumber}
 * is this application's own gapless reference, drawn on posting, exactly
 * like {@link Invoice#getInvoiceNumber()}; {@link #vendorReference} is the
 * number printed on the vendor's own bill, entered by whoever keys this one
 * in, and is what {@code BillService}'s duplicate-vendor-bill guard compares
 * against -- our own number can never collide with anything, so it cannot
 * catch the same bill being entered twice.
 */
@Entity
@Table(name = "bills")
public class Bill {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Column(name = "bill_number", length = 20)
    private String billNumber;

    @Column(name = "vendor_reference", length = 50)
    private String vendorReference;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private BillStatus status = BillStatus.DRAFT;

    @Column(name = "bill_date", nullable = false)
    private LocalDate billDate;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 1000)
    private String notes;

    @Column(name = "posted_transaction_id")
    private Long postedTransactionId;

    @Column(name = "posted_at")
    private OffsetDateTime postedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Long getContactId() {
        return contactId;
    }

    public void setContactId(Long contactId) {
        this.contactId = contactId;
    }

    public String getBillNumber() {
        return billNumber;
    }

    public void setBillNumber(String billNumber) {
        this.billNumber = billNumber;
    }

    public String getVendorReference() {
        return vendorReference;
    }

    public void setVendorReference(String vendorReference) {
        this.vendorReference = vendorReference;
    }

    public BillStatus getStatus() {
        return status;
    }

    public void setStatus(BillStatus status) {
        this.status = status;
    }

    public LocalDate getBillDate() {
        return billDate;
    }

    public void setBillDate(LocalDate billDate) {
        this.billDate = billDate;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }

    public Long getPostedTransactionId() {
        return postedTransactionId;
    }

    public void setPostedTransactionId(Long postedTransactionId) {
        this.postedTransactionId = postedTransactionId;
    }

    public OffsetDateTime getPostedAt() {
        return postedAt;
    }

    public void setPostedAt(OffsetDateTime postedAt) {
        this.postedAt = postedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
