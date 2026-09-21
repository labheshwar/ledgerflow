package com.ledgerflow.repository;

import com.ledgerflow.domain.InvoicePublicLink;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

/**
 * Not {@code @Transactional(readOnly = true)} at the interface level like
 * every other repository here -- this table carries no row-level security,
 * so there is no tenant transaction for a bare lookup to need. See
 * {@link com.ledgerflow.domain.InvoicePublicLink}'s own Javadoc.
 */
@Transactional
public interface InvoicePublicLinkRepository extends JpaRepository<InvoicePublicLink, String> {

    Optional<InvoicePublicLink> findByInvoiceId(Long invoiceId);
}
