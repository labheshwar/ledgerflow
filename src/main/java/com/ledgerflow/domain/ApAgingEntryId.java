package com.ledgerflow.domain;

import java.io.Serializable;
import java.util.Objects;

/** The composite key {@link ApAgingEntry} uses: one row per bill per org. */
public class ApAgingEntryId implements Serializable {

    private Long orgId;
    private Long billId;

    public ApAgingEntryId() {}

    public Long getOrgId() {
        return orgId;
    }

    public void setOrgId(Long orgId) {
        this.orgId = orgId;
    }

    public Long getBillId() {
        return billId;
    }

    public void setBillId(Long billId) {
        this.billId = billId;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ApAgingEntryId that)) return false;
        return Objects.equals(orgId, that.orgId) && Objects.equals(billId, that.billId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, billId);
    }
}
