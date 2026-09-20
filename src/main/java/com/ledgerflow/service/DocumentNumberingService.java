package com.ledgerflow.service;

import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.repository.DocumentNumberingRepository;
import com.ledgerflow.tenancy.TenantContext;
import org.springframework.stereotype.Service;

/**
 * The next document number in a gapless, per-organization series -- "INV-00003",
 * never a number a voided draft already claimed.
 *
 * Deliberately not {@code @Transactional}: the increment has to land inside
 * whatever transaction is creating the document itself, so that a rolled-back
 * document never consumes a number. Wrapping this in its own transaction
 * would instead let the increment commit independently and outlive the
 * document it was meant to number.
 */
@Service
public class DocumentNumberingService {

    private static final int PAD_WIDTH = 5;

    private final DocumentNumberingRepository documentNumberingRepository;

    public DocumentNumberingService(DocumentNumberingRepository documentNumberingRepository) {
        this.documentNumberingRepository = documentNumberingRepository;
    }

    public String next(DocumentType type) {
        Long orgId = TenantContext.require();
        long number = documentNumberingRepository.incrementAndGet(orgId, type);
        return type.prefix() + "-" + String.format("%0" + PAD_WIDTH + "d", number);
    }
}
