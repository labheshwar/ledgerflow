package com.ledgerflow.repository;

import com.ledgerflow.domain.Entry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface EntryRepository extends JpaRepository<Entry, Long> {

    /**
     * Ordered the way a ledger reads: by the date the entry is effective,
     * then by the transaction and entry that produced it.
     *
     * The ordering is load bearing, not cosmetic. A running balance is a
     * fold over this sequence, so ordering by created_at -- which is what
     * this did before transactions had an accounting date -- puts a
     * back-dated correction at the end and makes every running balance
     * before it wrong. Transaction and entry id break ties so two entries on
     * the same day always fold in the same order, which matters because an
     * unstable sort would make the same statement print differently twice.
     */
    @Query("""
            SELECT e FROM Entry e
            JOIN e.transaction t
            WHERE e.account.id = :accountId
            ORDER BY t.txnDate ASC, t.id ASC, e.id ASC
            """)
    List<Entry> findForLedger(@Param("accountId") Long accountId);

    /**
     * Fetches the account eagerly: without it, the account is a lazy
     * proxy that's only safe to read the ID off of (already cached on the
     * proxy) -- reading anything else, like the name the transaction
     * detail response needs, throws LazyInitializationException once the
     * repository call's own transaction has closed.
     */
    @Query("SELECT e FROM Entry e JOIN FETCH e.account WHERE e.transaction.id = :transactionId")
    List<Entry> findByTransactionId(@Param("transactionId") Long transactionId);
}
