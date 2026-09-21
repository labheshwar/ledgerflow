package com.ledgerflow.web;

import com.ledgerflow.domain.Attachment;
import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoiceLine;
import com.ledgerflow.domain.InvoiceStatus;
import com.ledgerflow.repository.ContactRepository;
import com.ledgerflow.service.AttachmentService;
import com.ledgerflow.service.InvoiceDeliveryService;
import com.ledgerflow.service.InvoiceService;
import com.ledgerflow.service.InvoiceTotals;
import com.ledgerflow.web.dto.AttachmentResponse;
import com.ledgerflow.web.dto.EmailInvoiceRequest;
import com.ledgerflow.web.dto.InvoiceDetailResponse;
import com.ledgerflow.web.dto.InvoiceRequest;
import com.ledgerflow.web.dto.InvoiceResponse;
import com.ledgerflow.web.dto.PagedResponse;
import com.ledgerflow.web.dto.PublicLinkResponse;
import com.ledgerflow.web.dto.VoidInvoiceRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
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
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/invoices")
public class InvoiceController {

    private static final String ENTITY_TYPE = "INVOICE";

    private static final Set<String> SORTABLE =
            Set.of("issueDate", "dueDate", "invoiceNumber", "createdAt", "updatedAt");

    private final InvoiceService invoiceService;
    private final InvoiceDeliveryService invoiceDeliveryService;
    private final AttachmentService attachmentService;
    private final ContactRepository contactRepository;

    public InvoiceController(
            InvoiceService invoiceService,
            InvoiceDeliveryService invoiceDeliveryService,
            AttachmentService attachmentService,
            ContactRepository contactRepository) {
        this.invoiceService = invoiceService;
        this.invoiceDeliveryService = invoiceDeliveryService;
        this.attachmentService = attachmentService;
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

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        Invoice invoice = invoiceService.get(id);
        byte[] pdf = invoiceDeliveryService.renderPdf(id);
        String filename = (invoice.getInvoiceNumber() == null ? "draft-invoice" : invoice.getInvoiceNumber()) + ".pdf";
        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment().filename(filename).build().toString())
                .body(pdf);
    }

    /** Idempotent: the same invoice always resolves to the same link once one exists. */
    @PostMapping("/{id}/public-link")
    public PublicLinkResponse getOrCreatePublicLink(@PathVariable Long id) {
        return new PublicLinkResponse(invoiceDeliveryService.publicUrlFor(invoiceDeliveryService.ensurePublicLink(id)));
    }

    /** Queued: the worker renders the PDF and talks to SMTP so this call returns immediately. */
    @PostMapping("/{id}/email")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void emailInvoice(@PathVariable Long id, @Valid @RequestBody EmailInvoiceRequest request) {
        invoiceDeliveryService.requestEmail(id, request.recipientEmail(), false);
    }

    @PostMapping("/{id}/remind")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void remindInvoice(@PathVariable Long id, @Valid @RequestBody EmailInvoiceRequest request) {
        invoiceDeliveryService.requestEmail(id, request.recipientEmail(), true);
    }

    @GetMapping("/{id}/attachments")
    public List<AttachmentResponse> listAttachments(@PathVariable Long id) {
        return attachmentService.list(ENTITY_TYPE, id).stream().map(AttachmentResponse::from).toList();
    }

    @PostMapping("/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse uploadAttachment(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        invoiceService.get(id); // 404s cleanly if the invoice does not exist, before touching storage
        try {
            String contentType = file.getContentType() == null ? "application/octet-stream" : file.getContentType();
            Attachment saved = attachmentService.upload(ENTITY_TYPE, id, file.getOriginalFilename(), contentType, file.getBytes());
            return AttachmentResponse.from(saved);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }

    @GetMapping("/{id}/attachments/{attachmentId}")
    public ResponseEntity<byte[]> downloadAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        Attachment attachment = attachmentService.get(attachmentId);
        byte[] content = attachmentService.download(attachmentId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(attachment.getContentType()))
                .header(
                        HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(attachment.getFilename()).build().toString())
                .body(content);
    }

    @DeleteMapping("/{id}/attachments/{attachmentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAttachment(@PathVariable Long id, @PathVariable Long attachmentId) {
        attachmentService.delete(attachmentId);
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
