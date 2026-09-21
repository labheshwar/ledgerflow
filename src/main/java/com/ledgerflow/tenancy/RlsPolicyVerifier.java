package com.ledgerflow.tenancy;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Refuses to start if a tenant-scoped table is missing row-level security.
 *
 * This is the only thing standing between "someone added a table and forgot
 * the policy" and a silent cross-tenant leak: the application keeps working
 * perfectly either way, so nothing else would notice. It also checks FORCE,
 * without which the owning role bypasses every policy and the protection is
 * theatre.
 */
@Component
public class RlsPolicyVerifier implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(RlsPolicyVerifier.class);

    /** Kept in step with the policies created in V10. */
    static final Set<String> TENANT_SCOPED_TABLES = Set.of(
            "accounts",
            "transactions",
            "entries",
            "audit_log",
            "reconciliation_batches",
            "reconciliation_results",
            "outbox_event",
            "account_balance_snapshots",
            "accounting_periods",
            "contacts",
            "tax_rates",
            "items",
            "document_number_counters",
            "invoices",
            "invoice_lines",
            "attachments",
            "bills",
            "bill_lines");
    // invoice_public_links is deliberately not here -- see V18's own
    // comment on that table for why it carries no tenant policy at all.

    private final JdbcTemplate jdbcTemplate;

    public RlsPolicyVerifier(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<String> unprotected = jdbcTemplate.queryForList(
                        """
                        SELECT c.relname
                        FROM pg_class c
                        JOIN pg_namespace n ON n.oid = c.relnamespace
                        WHERE n.nspname = 'public'
                          AND c.relkind = 'r'
                          AND (c.relrowsecurity = false OR c.relforcerowsecurity = false)
                        """,
                        String.class)
                .stream()
                .filter(TENANT_SCOPED_TABLES::contains)
                .toList();

        if (!unprotected.isEmpty()) {
            throw new IllegalStateException(
                    "Row-level security is not enabled and forced on: "
                            + unprotected.stream().sorted().collect(Collectors.joining(", "))
                            + ". Every tenant-scoped table needs a policy, or its rows leak across organizations.");
        }

        log.info("Row-level security verified on {} tenant-scoped tables", TENANT_SCOPED_TABLES.size());
    }
}
