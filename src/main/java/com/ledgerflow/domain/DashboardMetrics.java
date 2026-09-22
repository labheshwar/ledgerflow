package com.ledgerflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

/**
 * One row per organization: the dashboard's own numbers, last recomputed by
 * the projector on every {@code transaction.posted} event. Keyed on org_id
 * itself rather than a surrogate id -- there is exactly one of these per
 * tenant, ever.
 */
@Entity
@Table(name = "dashboard_metrics")
public class DashboardMetrics {

    @Id
    @Column(name = "org_id", nullable = false)
    private Long orgId;

    @Column(name = "account_count", nullable = false)
    private long accountCount;

    @Column(name = "total_ledger_balance", nullable = false, precision = 18, scale = 4)
    private BigDecimal totalLedgerBalance;

    @Column(name = "postings_today", nullable = false)
    private long postingsToday;

    @Column(name = "open_ar_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal openArTotal;

    @Column(name = "overdue_ar_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal overdueArTotal;

    @Column(name = "open_ap_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal openApTotal;

    @Column(name = "overdue_ap_total", nullable = false, precision = 18, scale = 4)
    private BigDecimal overdueApTotal;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public DashboardMetrics() {}

    public DashboardMetrics(
            Long orgId,
            long accountCount,
            BigDecimal totalLedgerBalance,
            long postingsToday,
            BigDecimal openArTotal,
            BigDecimal overdueArTotal,
            BigDecimal openApTotal,
            BigDecimal overdueApTotal) {
        this.orgId = orgId;
        this.accountCount = accountCount;
        this.totalLedgerBalance = totalLedgerBalance;
        this.postingsToday = postingsToday;
        this.openArTotal = openArTotal;
        this.overdueArTotal = overdueArTotal;
        this.openApTotal = openApTotal;
        this.overdueApTotal = overdueApTotal;
        this.updatedAt = OffsetDateTime.now();
    }

    public Long getOrgId() {
        return orgId;
    }

    public long getAccountCount() {
        return accountCount;
    }

    public BigDecimal getTotalLedgerBalance() {
        return totalLedgerBalance;
    }

    public long getPostingsToday() {
        return postingsToday;
    }

    public BigDecimal getOpenArTotal() {
        return openArTotal;
    }

    public BigDecimal getOverdueArTotal() {
        return overdueArTotal;
    }

    public BigDecimal getOpenApTotal() {
        return openApTotal;
    }

    public BigDecimal getOverdueApTotal() {
        return overdueApTotal;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
