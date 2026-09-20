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
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.OffsetDateTime;

@Entity
@Table(name = "accounts")
public class Account {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "org_id", nullable = false)
    private Long orgId;

    /** Unique within the organization; the stable handle a name is not. */
    @Column(nullable = false, length = 20)
    private String code;

    @Column(nullable = false)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 3)
    private String currency;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private AccountType type;

    /**
     * The parent heading, or null at the top of the chart. Held as an id
     * rather than a @ManyToOne: the tree is read whole and assembled in
     * memory, so an association would only invite a lazy proxy to be
     * dereferenced somewhere the session has already closed.
     */
    @Column(name = "parent_id")
    private Long parentId;

    /** What the application means by this account, if anything. */
    @Enumerated(EnumType.STRING)
    @Column(name = "system_role", length = 40)
    private SystemAccountRole systemRole;

    /**
     * Whether entries may land here. A heading with children beneath it
     * cannot receive them -- its total is the sum of its children, and an
     * entry posted directly to it would be counted twice.
     */
    @Column(name = "is_postable", nullable = false)
    private boolean postable = true;

    /**
     * When it was archived, or null while active. Accounts are never deleted
     * once posted to: the entries reference them and history has to stay
     * explicable. Archiving removes it from pickers and nothing else.
     */
    @Column(name = "archived_at")
    private OffsetDateTime archivedAt;

    /**
     * Still here, but no longer guarding a balance. Balances are derived from
     * the entries now, so a posting does not write to this row at all -- this
     * guards concurrent edits to the account itself, such as two people
     * renaming or re-typing it at once.
     */
    @Version
    @Column(nullable = false)
    private Long version;

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

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Long getParentId() {
        return parentId;
    }

    public void setParentId(Long parentId) {
        this.parentId = parentId;
    }

    public SystemAccountRole getSystemRole() {
        return systemRole;
    }

    public void setSystemRole(SystemAccountRole systemRole) {
        this.systemRole = systemRole;
    }

    public boolean isPostable() {
        return postable;
    }

    public void setPostable(boolean postable) {
        this.postable = postable;
    }

    public OffsetDateTime getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(OffsetDateTime archivedAt) {
        this.archivedAt = archivedAt;
    }

    public boolean isArchived() {
        return archivedAt != null;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public AccountType getType() {
        return type;
    }

    public void setType(AccountType type) {
        this.type = type;
    }

    public Long getVersion() {
        return version;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
