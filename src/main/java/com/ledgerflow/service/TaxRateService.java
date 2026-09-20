package com.ledgerflow.service;

import com.ledgerflow.domain.TaxRate;
import com.ledgerflow.exception.MasterDataException;
import com.ledgerflow.repository.TaxRateRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TaxRateService {

    private final TaxRateRepository taxRateRepository;

    public TaxRateService(TaxRateRepository taxRateRepository) {
        this.taxRateRepository = taxRateRepository;
    }

    @Transactional(readOnly = true)
    public Page<TaxRate> search(String q, boolean includeArchived, Pageable pageable) {
        return taxRateRepository.search(q, includeArchived, pageable);
    }

    @Transactional(readOnly = true)
    public TaxRate get(Long id) {
        return require(id);
    }

    @Transactional
    public TaxRate create(TaxRateDraft draft) {
        TaxRate taxRate = new TaxRate();
        taxRate.setOrgId(TenantContext.require());
        taxRate.setName(requireName(draft.name()));
        taxRate.setRate(requireRate(draft.rate()));
        return taxRateRepository.save(taxRate);
    }

    @Transactional
    public TaxRate update(Long id, TaxRateDraft draft) {
        TaxRate taxRate = require(id);
        taxRate.setName(requireName(draft.name()));
        taxRate.setRate(requireRate(draft.rate()));
        return taxRateRepository.save(taxRate);
    }

    @Transactional
    public TaxRate archive(Long id) {
        TaxRate taxRate = require(id);
        taxRate.setArchivedAt(OffsetDateTime.now());
        return taxRateRepository.save(taxRate);
    }

    @Transactional
    public TaxRate restore(Long id) {
        TaxRate taxRate = require(id);
        taxRate.setArchivedAt(null);
        return taxRateRepository.save(taxRate);
    }

    @Transactional
    public void delete(Long id) {
        taxRateRepository.delete(require(id));
    }

    private TaxRate require(Long id) {
        return taxRateRepository.findById(id).orElseThrow(() -> new NoSuchElementException("No tax rate with id " + id));
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new MasterDataException("INVALID_NAME", "A tax rate needs a name");
        }
        return name.trim();
    }

    private BigDecimal requireRate(BigDecimal rate) {
        if (rate == null) {
            throw new MasterDataException("INVALID_RATE", "A tax rate needs a percentage");
        }
        if (rate.signum() < 0 || rate.compareTo(BigDecimal.valueOf(100)) > 0) {
            throw new MasterDataException("INVALID_RATE", "A tax rate must be between 0 and 100");
        }
        return rate;
    }
}
