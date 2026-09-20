package com.ledgerflow.service;

import com.ledgerflow.domain.Item;
import com.ledgerflow.exception.MasterDataException;
import com.ledgerflow.repository.ItemRepository;
import com.ledgerflow.repository.TaxRateRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ItemService {

    private final ItemRepository itemRepository;
    private final TaxRateRepository taxRateRepository;

    public ItemService(ItemRepository itemRepository, TaxRateRepository taxRateRepository) {
        this.itemRepository = itemRepository;
        this.taxRateRepository = taxRateRepository;
    }

    @Transactional(readOnly = true)
    public Page<Item> search(String q, boolean includeArchived, Pageable pageable) {
        return itemRepository.search(q, includeArchived, pageable);
    }

    @Transactional(readOnly = true)
    public Item get(Long id) {
        return require(id);
    }

    @Transactional
    public Item create(ItemDraft draft) {
        Item item = new Item();
        item.setOrgId(TenantContext.require());
        apply(item, draft);
        return save(item);
    }

    @Transactional
    public Item update(Long id, ItemDraft draft) {
        Item item = require(id);
        apply(item, draft);
        return save(item);
    }

    @Transactional
    public Item archive(Long id) {
        Item item = require(id);
        item.setArchivedAt(OffsetDateTime.now());
        return itemRepository.save(item);
    }

    @Transactional
    public Item restore(Long id) {
        Item item = require(id);
        item.setArchivedAt(null);
        return itemRepository.save(item);
    }

    @Transactional
    public void delete(Long id) {
        itemRepository.delete(require(id));
    }

    private void apply(Item item, ItemDraft draft) {
        item.setSku(blankToNull(draft.sku()));
        item.setName(requireName(draft.name()));
        item.setDescription(blankToNull(draft.description()));
        item.setDefaultUnitPrice(requirePriceOrNull(draft.defaultUnitPrice()));
        item.setDefaultTaxRateId(requireTaxRateOrNull(draft.defaultTaxRateId()));
    }

    private Item require(Long id) {
        return itemRepository.findById(id).orElseThrow(() -> new NoSuchElementException("No item with id " + id));
    }

    private Item save(Item item) {
        try {
            return itemRepository.saveAndFlush(item);
        } catch (DataIntegrityViolationException e) {
            // Flushing inside the try is what makes this land here, attributed
            // to the request that caused it, rather than at commit.
            throw new MasterDataException(
                    "DUPLICATE_SKU", "Another item already uses SKU %s".formatted(item.getSku()));
        }
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new MasterDataException("INVALID_NAME", "An item needs a name");
        }
        return name.trim();
    }

    private BigDecimal requirePriceOrNull(BigDecimal price) {
        if (price == null) {
            return null;
        }
        if (price.signum() < 0) {
            throw new MasterDataException("INVALID_PRICE", "The default unit price cannot be negative");
        }
        return price;
    }

    private Long requireTaxRateOrNull(Long taxRateId) {
        if (taxRateId == null) {
            return null;
        }
        if (!taxRateRepository.existsById(taxRateId)) {
            throw new MasterDataException("INVALID_TAX_RATE", "No tax rate with id " + taxRateId);
        }
        return taxRateId;
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
