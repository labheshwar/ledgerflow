package com.ledgerflow.web;

import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoiceLine;
import com.ledgerflow.domain.InvoiceStatus;
import com.ledgerflow.repository.ContactRepository;
import com.ledgerflow.service.InvoiceService;
import com.ledgerflow.service.InvoiceTotals;
import com.ledgerflow.web.dto.InvoiceDetailResponse;
import com.ledgerflow.web.dto.InvoiceRequest;
import com.ledgerflow.web.dto.InvoiceResponse;
import com.ledgerflow.web.dto.PagedResponse;
import com.ledgerflow.web.dto.VoidInvoiceRequest;
import jakarta.validation.Valid;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
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
@RequestMapping("/invoices")
public class InvoiceController {

    private static final Set<String> SORTABLE =
            Set.of("issueDate", "dueDate", "invoiceNumber", "createdAt", "updatedAt");

    private final InvoiceService invoiceService;
    private final ContactRepository contactRepository;

    public InvoiceController(InvoiceService invoiceService, ContactRepository contactRepository) {
        this.invoiceService = invoiceService;
        this.contactRepository = contactRepository;
    }

    @GetMapping
    public PagedResponse<InvoiceResponse> listInvoices(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) InvoiceStatus status,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {

        Pageable sorted = SortWhitelist.apply(pageable, SORTABLE, Sort.by("issueDate").descending());
        Page<Invoice> page = invoiceService.search(q, status, sorted);
        return PagedResponse.of(page.getContent().stream().map(this::toResponse).toList(), page);
    }

    @GetMapping("/{id}")
    public InvoiceDetailResponse getInvoice(@PathVariable Long id) {
        return toDetailResponse(invoiceService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public InvoiceDetailResponse createInvoice(@Valid @RequestBody InvoiceRequest request) {
        return toDetailResponse(invoiceService.create(request.toDraft()));
    }

    @PutMapping("/{id}")
    public InvoiceDetailResponse updateInvoice(@PathVariable Long id, @Valid @RequestBody InvoiceRequest request) {
        return toDetailResponse(invoiceService.update(id, request.toDraft()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteInvoice(@PathVariable Long id) {
        invoiceService.delete(id);
    }

    @PostMapping("/{id}/send")
    public InvoiceDetailResponse sendInvoice(@PathVariable Long id) {
        return toDetailResponse(invoiceService.send(id));
    }

    @PostMapping("/{id}/void")
    public InvoiceDetailResponse voidInvoice(@PathVariable Long id, @RequestBody(required = false) VoidInvoiceRequest request) {
        String reason = request == null ? null : request.reason();
        return toDetailResponse(invoiceService.voidInvoice(id, reason));
    }

    private InvoiceResponse toResponse(Invoice invoice) {
        List<InvoiceLine> lines = invoiceService.getLines(invoice.getId());
        InvoiceTotals totals = invoiceService.totalsFor(lines);
        return InvoiceResponse.from(invoice, contactName(invoice.getContactId()), totals);
    }

    private InvoiceDetailResponse toDetailResponse(Invoice invoice) {
        List<InvoiceLine> lines = invoiceService.getLines(invoice.getId());
        InvoiceTotals totals = invoiceService.totalsFor(lines);
        return InvoiceDetailResponse.from(invoice, contactName(invoice.getContactId()), lines, totals);
    }

    private String contactName(Long contactId) {
        return contactRepository
                .findById(contactId)
                .map(Contact::getName)
                .orElseThrow(() -> new NoSuchElementException("No contact with id " + contactId));
    }
}
