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
import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "transactions")
public class Transaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    /** Unique per organization, not globally -- see V9. */
    @Column(name = "idempotency_key", nullable = false)
    private String idempotencyKey;

    /**
     * The date the transaction is effective for accounting, which is not the
     * same fact as when the row was written. An invoice dated Friday but
     * entered on Monday belongs in Friday's books.
     */
    @Column(name = "txn_date", nullable = false)
    private LocalDate txnDate;

    @Column
    private String description;

    /**
     * The transaction this one undoes, or null for an ordinary posting.
     *
     * Whether the *original* has been reversed is deliberately not a column
     * on that row -- it is answered by looking for a transaction whose
     * reversalOfTransactionId points back at it. A status flag would be a
     * second place for that fact to live, and the two could disagree; a
     * pointer that only the reversal itself carries cannot.
     */
    @Column(name = "reversal_of_transaction_id")
    private Long reversalOfTransactionId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private TransactionStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        if (status == null) {
            status = TransactionStatus.POSTED;
        }
        if (txnDate == null) {
            txnDate = createdAt.toLocalDate();
        }
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

    public String getIdempotencyKey() {
        return idempotencyKey;
    }

    public void setIdempotencyKey(String idempotencyKey) {
        this.idempotencyKey = idempotencyKey;
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

    public Long getReversalOfTransactionId() {
        return reversalOfTransactionId;
    }

    public void setReversalOfTransactionId(Long reversalOfTransactionId) {
        this.reversalOfTransactionId = reversalOfTransactionId;
    }

    public TransactionStatus getStatus() {
        return status;
    }

    public void setStatus(TransactionStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }
}
