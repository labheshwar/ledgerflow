package com.ledgerflow.repository;

import com.ledgerflow.domain.DocumentType;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

/**
 * The next number in a gapless, per-organization, per-document-type series.
 *
 * Plain JDBC rather than a JPA entity: the whole point is a single
 * atomic statement, and going through the persistence context would only
 * invite a stale in-memory copy of a row another request just incremented.
 * It borrows the connection already bound to the caller's transaction --
 * the same reason {@code AccountBalanceQueries} does -- so the increment
 * commits or rolls back with whatever document the number is for, rather
 * than surviving a rollback the way a Postgres SEQUENCE would.
 */
@Repository
public class DocumentNumberingRepository {

    private final NamedParameterJdbcTemplate jdbc;

    public DocumentNumberingRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * Inserts the counter at 1 on first use, or increments the existing one --
     * one round trip, one row lock, no race between "does a counter exist"
     * and "increment it" for two requests to fall into at once.
     */
    public long incrementAndGet(Long orgId, DocumentType docType) {
        return jdbc.queryForObject(
                """
                INSERT INTO document_number_counters (org_id, doc_type, last_number)
                VALUES (:orgId, :docType, 1)
                ON CONFLICT (org_id, doc_type)
                DO UPDATE SET last_number = document_number_counters.last_number + 1
                RETURNING last_number
                """,
                new MapSqlParameterSource()
                        .addValue("orgId", orgId)
                        .addValue("docType", docType.name()),
                Long.class);
    }
}
