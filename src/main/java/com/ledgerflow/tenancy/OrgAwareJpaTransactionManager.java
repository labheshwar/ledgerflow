package com.ledgerflow.tenancy;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.Session;
import org.springframework.orm.jpa.EntityManagerHolder;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/**
 * Publishes the current tenant to Postgres so the row-level security policies
 * in V10 have something to compare against.
 *
 * This belongs in the transaction manager and nowhere else. A servlet filter
 * runs before a connection is checked out; HikariCP's connectionInitSql is
 * session-scoped and would leak the value to whoever borrows the connection
 * next; a TransactionSynchronization runs after queries may already have
 * gone out. doBegin is the one place we are guaranteed to hold the exact
 * connection this transaction will use, before any statement runs on it.
 *
 * set_config(..., true) rather than SET LOCAL because SET LOCAL cannot take a
 * bind parameter -- and string-concatenating a tenant id into DDL-ish SQL is
 * how you get an injection. The `true` makes it transaction-scoped, so
 * Postgres reverts it at commit or rollback and nothing survives back into
 * the pool.
 */
public class OrgAwareJpaTransactionManager extends JpaTransactionManager {

    @Override
    protected void doBegin(Object transaction, TransactionDefinition definition) {
        super.doBegin(transaction, definition);

        Long orgId = TenantContext.get();
        if (orgId == null) {
            // Deliberately not an error: login, signup and the startup checks
            // all run without a tenant. The policies fail closed, so an
            // unscoped read of a tenant table returns nothing.
            return;
        }

        EntityManagerFactory emf = getEntityManagerFactory();
        if (emf == null) {
            return;
        }

        EntityManagerHolder holder =
                (EntityManagerHolder) TransactionSynchronizationManager.getResource(emf);
        if (holder == null) {
            return;
        }

        EntityManager entityManager = holder.getEntityManager();
        entityManager.unwrap(Session.class).doWork(connection -> {
            try (var statement = connection.prepareStatement("SELECT set_config('app.current_org', ?, true)")) {
                statement.setString(1, String.valueOf(orgId));
                statement.execute();
            }
        });
    }
}
