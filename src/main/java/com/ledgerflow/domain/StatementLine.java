package com.ledgerflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

/**
 * One row of an imported bank statement. Signed the way a statement itself
 * reads -- positive for money in, negative for money out -- rather than as
 * a debit/credit pair, since nothing has matched it to the ledger yet;
 * milestone 14's reconciliation workspace is what turns one of these into
 * an actual posted entry.
 *
 * A row exists here only once it is known not to be a duplicate of an
 * already-committed line -- see the partial unique index on
 * {@code (bank_account_id, external_id) WHERE committed}, the same
 * committed-rows-only-count shape the duplicate-vendor-bill guard already
 * uses for bills. {@code committed = false} is a staged preview row,
 * deleted and reinserted if the same import is re-previewed with a
 * different mapping, and never counted toward another import's own dedup
 * check until it is actually committed.
 *
 * {@code matchedEntryId} is milestone 14's own reconciliation workspace:
 * once set, this line is accounted for by that entry -- one it already
 * matched to, or one a payment or manual categorization just posted on
 * its behalf -- enforced one-to-one by a partial unique index on that
 * column, the same shape the dedupe guard above uses.
 */
@Entity
@Table(name = "statement_lines")
public class StatementLine {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "bank_account_id", nullable = false)
    private Long bankAccountId;

    @Column(name = "import_id", nullable = false)
    private Long importId;

    @Column(name = "external_id", nullable = false)
    private String externalId;

    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column(nullable = false, length = 500)
    private String description;

    @Column(nullable = false, precision = 18, scale = 4)
    private BigDecimal amount;

    @Column(nullable = false)
    private boolean committed;

    @Column(name = "matched_entry_id")
    private Long matchedEntryId;

    @Column(name = "matched_at")
    private OffsetDateTime matchedAt;

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

    public Long getBankAccountId() {
        return bankAccountId;
    }

    public void setBankAccountId(Long bankAccountId) {
        this.bankAccountId = bankAccountId;
    }

    public Long getImportId() {
        return importId;
    }

    public void setImportId(Long importId) {
        this.importId = importId;
    }

    public String getExternalId() {
        return externalId;
    }

    public void setExternalId(String externalId) {
        this.externalId = externalId;
    }

    public LocalDate getTxnDate() {
        return txnDate;
    }

    public void setTxnDate(LocalDate txnDate) {
        this.txnDate = txnDate;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public boolean isCommitted() {
        return committed;
    }

    public void setCommitted(boolean committed) {
        this.committed = committed;
    }

    public Long getMatchedEntryId() {
        return matchedEntryId;
    }

    public void setMatchedEntryId(Long matchedEntryId) {
        this.matchedEntryId = matchedEntryId;
    }

    public OffsetDateTime getMatchedAt() {
        return matchedAt;
    }

    public void setMatchedAt(OffsetDateTime matchedAt) {
        this.matchedAt = matchedAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
