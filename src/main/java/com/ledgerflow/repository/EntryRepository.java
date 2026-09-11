package com.ledgerflow.repository;

import com.ledgerflow.domain.Entry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface EntryRepository extends JpaRepository<Entry, Long> {

    List<Entry> findByAccountIdOrderByCreatedAtAsc(Long accountId);

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
