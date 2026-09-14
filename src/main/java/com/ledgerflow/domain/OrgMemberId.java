package com.ledgerflow.domain;

import java.io.Serializable;
import java.util.Objects;

/** Composite key for {@link OrgMember}. */
public class OrgMemberId implements Serializable {

    private Long orgId;
    private Long userId;

    public OrgMemberId() {}

    public OrgMemberId(Long orgId, Long userId) {
        this.orgId = orgId;
        this.userId = userId;
    }

    public Long getOrgId() {
        return orgId;
    }

    public Long getUserId() {
        return userId;
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) return true;
        if (!(other instanceof OrgMemberId that)) return false;
        return Objects.equals(orgId, that.orgId) && Objects.equals(userId, that.userId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orgId, userId);
    }
}
