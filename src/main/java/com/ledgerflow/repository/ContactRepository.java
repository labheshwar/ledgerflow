package com.ledgerflow.repository;

import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.ContactType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface ContactRepository extends JpaRepository<Contact, Long> {

    /** Blank matches everything; see AccountRepository.search for why null is normalized away first. */
    default Page<Contact> search(String q, ContactType type, boolean includeArchived, Pageable pageable) {
        return searchFiltered(q == null ? "" : q.trim(), type, includeArchived, pageable);
    }

    @Query("""
            SELECT c FROM Contact c
            WHERE (:type IS NULL OR c.type = :type OR c.type = com.ledgerflow.domain.ContactType.BOTH)
              AND (:includeArchived = true OR c.archivedAt IS NULL)
              AND (:q = ''
                   OR LOWER(c.name) LIKE LOWER(CONCAT('%', :q, '%'))
                   OR LOWER(c.email) LIKE LOWER(CONCAT('%', :q, '%')))
            """)
    Page<Contact> searchFiltered(
            @Param("q") String q,
            @Param("type") ContactType type,
            @Param("includeArchived") boolean includeArchived,
            Pageable pageable);
}
