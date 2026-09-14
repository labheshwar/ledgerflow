package com.ledgerflow.tenancy;

import java.util.function.Supplier;

/**
 * The organization the current thread is acting for. Set at every edge that
 * starts work -- the HTTP filter, the message listeners, scheduled jobs --
 * and read by {@link OrgAwareJpaTransactionManager} when a transaction opens.
 */
public final class TenantContext {

    private static final ThreadLocal<Long> CURRENT_ORG = new ThreadLocal<>();

    private TenantContext() {}

    public static void set(Long orgId) {
        CURRENT_ORG.set(orgId);
    }

    public static Long get() {
        return CURRENT_ORG.get();
    }

    public static Long require() {
        Long orgId = CURRENT_ORG.get();
        if (orgId == null) {
            throw new IllegalStateException("No organization in context for this thread");
        }
        return orgId;
    }

    public static void clear() {
        CURRENT_ORG.remove();
    }

    /**
     * Runs work for one organization and restores whatever was set before,
     * so a scheduled job looping over tenants can't leak context between
     * iterations.
     */
    public static <T> T runAs(Long orgId, Supplier<T> work) {
        Long previous = CURRENT_ORG.get();
        CURRENT_ORG.set(orgId);
        try {
            return work.get();
        } finally {
            if (previous == null) {
                CURRENT_ORG.remove();
            } else {
                CURRENT_ORG.set(previous);
            }
        }
    }

    public static void runAs(Long orgId, Runnable work) {
        runAs(orgId, () -> {
            work.run();
            return null;
        });
    }
}
