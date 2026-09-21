package com.ledgerflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * A header only -- its allocations live in {@link PaymentAllocation}, joined
 * by {@code paymentId} rather than a JPA collection, for the same reason
 * {@link Invoice} joins {@link InvoiceLine} that way.
 *
 * {@code amount} is the whole payment, which can be more than the sum of
 * its allocations: the remainder is what {@link com.ledgerflow.service.PaymentService}
 * posts to the customer's or vendor's prepayment account rather than to any
 * one invoice or bill. There is no draft stage -- see {@link PaymentStatus}.
 *
 * {@code bankAccountId} is null for a payment recorded the ordinary way,
 * which still posts its cash side to the organization's one system CASH
 * account exactly as before milestone 14. The reconciliation workspace
 * sets it when a statement line is settled against an invoice or bill, so
 * the cash leg lands on that specific bank account's own ledger account
 * instead -- otherwise the resulting entry could never be matched back to
 * the line that caused it.
 */
@Entity
@Table(name = "payments")
public class Payment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "contact_id", nullable = false)
    private Long contactId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentDirection direction;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private PaymentStatus status = PaymentStatus.POSTED;

    @Column(name = "payment_date", nullable = false)
    private LocalDate paymentDate;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(length = 1000)
    private String notes;

    @Column(name = "bank_account_id")
    private Long bankAccountId;

    /** Null only in the crash window between posting and this write-back -- see {@code PaymentSweeper}. */
    @Column(name = "posted_transaction_id")
    private Long postedTransactionId;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
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

    public PaymentDirection getDirection() {
        return direction;
    }

    public void setDirection(PaymentDirection direction) {
        this.direction = direction;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public LocalDate getPaymentDate() {
        return paymentDate;
    }

    public void setPaymentDate(LocalDate paymentDate) {
        this.paymentDate = paymentDate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
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

    public Long getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(Long bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public Long getPostedTransactionId() {
        return postedTransactionId;
    }

    public void setPostedTransactionId(Long postedTransactionId) {
        this.postedTransactionId = postedTransactionId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
