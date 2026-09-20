package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.ContactType;
import com.ledgerflow.domain.Item;
import com.ledgerflow.domain.TaxRate;
import com.ledgerflow.exception.MasterDataException;
import com.ledgerflow.service.ContactDraft;
import com.ledgerflow.service.ContactService;
import com.ledgerflow.service.ItemDraft;
import com.ledgerflow.service.ItemService;
import com.ledgerflow.service.TaxRateDraft;
import com.ledgerflow.service.TaxRateService;
import java.math.BigDecimal;
import java.util.NoSuchElementException;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

/**
 * Contacts, tax rates and items -- reference data an invoice or bill will
 * point at from milestone 9 onward. Exercised against real Postgres because
 * the SKU uniqueness rule (idx_items_org_sku) is a database constraint, and a
 * mocked repository would have to fake the exact violation ItemService.save()
 * catches -- the thing actually worth proving.
 */
class MasterDataIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private ContactService contactService;

    @Autowired
    private TaxRateService taxRateService;

    @Autowired
    private ItemService itemService;

    // --- contacts ---

    @Test
    void aContactCanBeCreatedUpdatedArchivedAndRestored() {
        Contact created = contactService.create(
                new ContactDraft(ContactType.CUSTOMER, "Acme Widgets", "billing@acme.test", null, null, null, null,
                        null, null, null, null, null));
        assertThat(created.getOrgId()).isEqualTo(DEMO_ORG_ID);
        assertThat(created.isArchived()).isFalse();

        Contact updated = contactService.update(
                created.getId(),
                new ContactDraft(ContactType.BOTH, "Acme Widgets Ltd", "ap@acme.test", "555-0100", null, null, null,
                        null, null, null, null, null));
        assertThat(updated.getName()).isEqualTo("Acme Widgets Ltd");
        assertThat(updated.getType()).isEqualTo(ContactType.BOTH);
        assertThat(updated.getPhone()).isEqualTo("555-0100");

        Contact archived = contactService.archive(created.getId());
        assertThat(archived.isArchived()).isTrue();

        Contact restored = contactService.restore(created.getId());
        assertThat(restored.isArchived()).isFalse();
    }

    @Test
    void aContactWithoutANameIsRefused() {
        assertThatThrownBy(() -> contactService.create(
                        new ContactDraft(ContactType.CUSTOMER, "  ", null, null, null, null, null, null, null, null,
                                null, null)))
                .isInstanceOf(MasterDataException.class)
                .satisfies(e -> assertThat(((MasterDataException) e).getCode()).isEqualTo("INVALID_NAME"));
    }

    @Test
    void searchFindsAContactByNameOrEmailAndHidesArchivedByDefault() {
        String tag = UUID.randomUUID().toString().substring(0, 8);
        Contact vendor = contactService.create(new ContactDraft(
                ContactType.VENDOR, "Search Target " + tag, "findme-" + tag + "@example.test", null, null, null,
                null, null, null, null, null, null));
        contactService.archive(vendor.getId());

        var activeOnly = contactService.search("findme-" + tag, null, false, PageRequest.of(0, 10));
        assertThat(activeOnly.getContent()).isEmpty();

        var includingArchived = contactService.search("findme-" + tag, null, true, PageRequest.of(0, 10));
        assertThat(includingArchived.getContent()).extracting(Contact::getId).containsExactly(vendor.getId());
    }

    @Test
    void deletingAContactThatWasNeverUsedRemovesItOutright() {
        Contact contact = contactService.create(new ContactDraft(
                ContactType.CUSTOMER, "Delete Me " + UUID.randomUUID(), null, null, null, null, null, null, null,
                null, null, null));

        contactService.delete(contact.getId());

        assertThatThrownBy(() -> contactService.get(contact.getId())).isInstanceOf(NoSuchElementException.class);
    }

    // --- tax rates ---

    @Test
    void aTaxRateCanBeCreatedUpdatedArchivedAndRestored() {
        TaxRate created = taxRateService.create(new TaxRateDraft("Standard VAT", new BigDecimal("15.000")));
        assertThat(created.getRate()).isEqualByComparingTo("15.000");

        TaxRate updated = taxRateService.update(created.getId(), new TaxRateDraft("Reduced VAT", new BigDecimal("5")));
        assertThat(updated.getName()).isEqualTo("Reduced VAT");
        assertThat(updated.getRate()).isEqualByComparingTo("5");

        assertThat(taxRateService.archive(created.getId()).isArchived()).isTrue();
        assertThat(taxRateService.restore(created.getId()).isArchived()).isFalse();
    }

    @Test
    void aTaxRateOutsideZeroToOneHundredIsRefused() {
        assertThatThrownBy(() -> taxRateService.create(new TaxRateDraft("Bogus", new BigDecimal("150"))))
                .isInstanceOf(MasterDataException.class)
                .satisfies(e -> assertThat(((MasterDataException) e).getCode()).isEqualTo("INVALID_RATE"));

        assertThatThrownBy(() -> taxRateService.create(new TaxRateDraft("Bogus", new BigDecimal("-1"))))
                .isInstanceOf(MasterDataException.class)
                .satisfies(e -> assertThat(((MasterDataException) e).getCode()).isEqualTo("INVALID_RATE"));
    }

    // --- items ---

    @Test
    void anItemCanBeCreatedUpdatedArchivedAndRestored() {
        TaxRate taxRate = taxRateService.create(new TaxRateDraft("Item Test Rate", new BigDecimal("10")));

        Item created = itemService.create(new ItemDraft(
                "SKU-" + UUID.randomUUID(), "Consulting Hour", "One hour of consulting", new BigDecimal("100.00"),
                taxRate.getId()));
        assertThat(created.getDefaultTaxRateId()).isEqualTo(taxRate.getId());

        Item updated = itemService.update(
                created.getId(), new ItemDraft(created.getSku(), "Consulting Hour (Senior)", null,
                        new BigDecimal("150.00"), null));
        assertThat(updated.getName()).isEqualTo("Consulting Hour (Senior)");
        assertThat(updated.getDefaultUnitPrice()).isEqualByComparingTo("150.00");
        assertThat(updated.getDefaultTaxRateId()).isNull();

        assertThat(itemService.archive(created.getId()).isArchived()).isTrue();
        assertThat(itemService.restore(created.getId()).isArchived()).isFalse();
    }

    @Test
    void twoItemsInTheSameOrganizationCannotShareASku() {
        String sku = "DUP-" + UUID.randomUUID();
        itemService.create(new ItemDraft(sku, "First", null, null, null));

        assertThatThrownBy(() -> itemService.create(new ItemDraft(sku, "Second", null, null, null)))
                .isInstanceOf(MasterDataException.class)
                .satisfies(e -> assertThat(((MasterDataException) e).getCode()).isEqualTo("DUPLICATE_SKU"));
    }

    @Test
    void anItemMayReferenceOnlyATaxRateThatActuallyExists() {
        assertThatThrownBy(() -> itemService.create(new ItemDraft(null, "Bad Reference", null, null, 999_999_999L)))
                .isInstanceOf(MasterDataException.class)
                .satisfies(e -> assertThat(((MasterDataException) e).getCode()).isEqualTo("INVALID_TAX_RATE"));
    }
}
