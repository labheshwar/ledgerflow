package com.ledgerflow.web;

import com.ledgerflow.domain.ContactType;
import com.ledgerflow.service.ContactService;
import com.ledgerflow.web.dto.ContactRequest;
import com.ledgerflow.web.dto.ContactResponse;
import com.ledgerflow.web.dto.PagedResponse;
import jakarta.validation.Valid;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/contacts")
public class ContactController {

    private static final Set<String> SORTABLE = Set.of("name", "type", "createdAt", "updatedAt");

    private final ContactService contactService;

    public ContactController(ContactService contactService) {
        this.contactService = contactService;
    }

    @GetMapping
    public PagedResponse<ContactResponse> listContacts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) ContactType type,
            @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {

        Pageable sorted = SortWhitelist.apply(pageable, SORTABLE, Sort.by("name").ascending());
        return PagedResponse.from(
                contactService.search(q, type, includeArchived, sorted), ContactResponse::from);
    }

    @GetMapping("/{id}")
    public ContactResponse getContact(@PathVariable Long id) {
        return ContactResponse.from(contactService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ContactResponse createContact(@Valid @RequestBody ContactRequest request) {
        return ContactResponse.from(contactService.create(request.toDraft()));
    }

    @PutMapping("/{id}")
    public ContactResponse updateContact(@PathVariable Long id, @Valid @RequestBody ContactRequest request) {
        return ContactResponse.from(contactService.update(id, request.toDraft()));
    }

    @PostMapping("/{id}/archive")
    public ContactResponse archiveContact(@PathVariable Long id) {
        return ContactResponse.from(contactService.archive(id));
    }

    @PostMapping("/{id}/restore")
    public ContactResponse restoreContact(@PathVariable Long id) {
        return ContactResponse.from(contactService.restore(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteContact(@PathVariable Long id) {
        contactService.delete(id);
    }
}
