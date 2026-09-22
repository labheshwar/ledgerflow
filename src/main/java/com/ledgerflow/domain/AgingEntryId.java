package com.ledgerflow.domain;

import java.io.Serializable;
import java.util.Objects;

/** The composite key {@link ArAgingEntry} uses: one row per invoice per org. */
public class AgingEntryId implements Serializable {

    private Long orgId;
    private Long invoiceId;

    public AgingEntryId() {}

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

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof AgingEntryId that)) return false;
        return Objects.equals(orgId, that.orgId) && Objects.equals(invoiceId, that.invoiceId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, invoiceId);
    }
}
