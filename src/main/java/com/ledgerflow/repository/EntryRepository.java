package com.ledgerflow.repository;

import com.ledgerflow.domain.Entry;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EntryRepository extends JpaRepository<Entry, Long> {

    List<Entry> findByAccountId(Long accountId);
}
