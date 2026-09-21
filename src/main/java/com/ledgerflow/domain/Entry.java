package com.ledgerflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "entries")
public class Entry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "transaction_id", nullable = false)
    private Transaction transaction;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private Account account;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 6)
    private EntryType entryType;

    /** In the currency the transaction was denominated in. */
    @Column(nullable = false)
    private BigDecimal amount;

    @Column(nullable = false, length = 3)
    private String currency;

    /**
     * The same value in the organization's reporting currency, converted at
     * fxRate. Frozen at posting time, so a report covering last year does not
     * change its answer every time today's exchange rate moves.
     */
    @Column(name = "base_amount", nullable = false)
    private BigDecimal baseAmount;

    @Column(name = "fx_rate", nullable = false)
    private BigDecimal fxRate;

    /**
     * Marks the realized gain/loss leg a foreign-currency settlement posts
     * when the rate at settlement differs from the rate its receivable was
     * booked at. Always in the organization's own base currency -- an FX
     * adjustment has no foreign-currency face value of its own -- and its
     * own boolean rather than inferred from which account it landed on, so
     * a report can separate "the business earned this" from "the exchange
     * rate moved" without having to know the chart of accounts.
     */
    @Column(name = "is_fx_adjustment", nullable = false)
    private boolean fxAdjustment;

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

    public Transaction getTransaction() {
        return transaction;
    }

    public void setTransaction(Transaction transaction) {
        this.transaction = transaction;
    }

    public Account getAccount() {
        return account;
    }

    public void setAccount(Account account) {
        this.account = account;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
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

    public BigDecimal getBaseAmount() {
        return baseAmount;
    }

    public void setBaseAmount(BigDecimal baseAmount) {
        this.baseAmount = baseAmount;
    }

    public BigDecimal getFxRate() {
        return fxRate;
    }

    public void setFxRate(BigDecimal fxRate) {
        this.fxRate = fxRate;
    }

    public boolean isFxAdjustment() {
        return fxAdjustment;
    }

    public void setFxAdjustment(boolean fxAdjustment) {
        this.fxAdjustment = fxAdjustment;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
