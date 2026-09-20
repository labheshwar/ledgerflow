package com.ledgerflow.service;

import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.ContactType;
import com.ledgerflow.exception.MasterDataException;
import com.ledgerflow.repository.ContactRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Customers and vendors -- CRUD plus the one house rule: archive, never delete, once anything can reference one. */
@Service
public class ContactService {

    private final ContactRepository contactRepository;

    public ContactService(ContactRepository contactRepository) {
        this.contactRepository = contactRepository;
    }

    @Transactional(readOnly = true)
    public Page<Contact> search(String q, ContactType type, boolean includeArchived, Pageable pageable) {
        return contactRepository.search(q, type, includeArchived, pageable);
    }

    @Transactional(readOnly = true)
    public Contact get(Long id) {
        return require(id);
    }

    @Transactional
    public Contact create(ContactDraft draft) {
        Contact contact = new Contact();
        contact.setOrgId(TenantContext.require());
        apply(contact, draft);
        return contactRepository.save(contact);
    }

    @Transactional
    public Contact update(Long id, ContactDraft draft) {
        Contact contact = require(id);
        apply(contact, draft);
        return contactRepository.save(contact);
    }

    @Transactional
    public Contact archive(Long id) {
        Contact contact = require(id);
        contact.setArchivedAt(OffsetDateTime.now());
        return contactRepository.save(contact);
    }

    @Transactional
    public Contact restore(Long id) {
        Contact contact = require(id);
        contact.setArchivedAt(null);
        return contactRepository.save(contact);
    }

    /**
     * Deletion is for a contact created by mistake, before an invoice or a
     * bill ever names it. Once something else can reference a contact
     * (milestone 9 onward), that reference is what makes a delete unsafe --
     * there is nothing to check for yet, so nothing is checked yet.
     */
    @Transactional
    public void delete(Long id) {
        contactRepository.delete(require(id));
    }

    private void apply(Contact contact, ContactDraft draft) {
        contact.setType(requireType(draft.type()));
        contact.setName(requireName(draft.name()));
        contact.setEmail(blankToNull(draft.email()));
        contact.setPhone(blankToNull(draft.phone()));
        contact.setTaxId(blankToNull(draft.taxId()));
        contact.setAddressLine1(blankToNull(draft.addressLine1()));
        contact.setAddressLine2(blankToNull(draft.addressLine2()));
        contact.setCity(blankToNull(draft.city()));
        contact.setState(blankToNull(draft.state()));
        contact.setPostalCode(blankToNull(draft.postalCode()));
        contact.setCountry(blankToNull(draft.country()));
        contact.setNotes(blankToNull(draft.notes()));
    }

    private Contact require(Long id) {
        return contactRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("No contact with id " + id));
    }

    private ContactType requireType(ContactType type) {
        if (type == null) {
            throw new MasterDataException("INVALID_TYPE", "A contact needs a type");
        }
        return type;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new MasterDataException("INVALID_NAME", "A contact needs a name");
        }
        return name.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
