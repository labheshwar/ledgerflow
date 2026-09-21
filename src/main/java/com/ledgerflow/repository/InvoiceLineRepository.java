package com.ledgerflow.repository;

import com.ledgerflow.domain.InvoiceLine;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface InvoiceLineRepository extends JpaRepository<InvoiceLine, Long> {

    List<InvoiceLine> findByInvoiceIdOrderByLineOrderAsc(Long invoiceId);

    @Modifying
    @Transactional
    void deleteByInvoiceId(Long invoiceId);
}
