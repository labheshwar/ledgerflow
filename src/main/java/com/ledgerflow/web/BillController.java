package com.ledgerflow.web;

import com.ledgerflow.domain.Attachment;
import com.ledgerflow.domain.Bill;
import com.ledgerflow.domain.BillLine;
import com.ledgerflow.domain.BillStatus;
import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.repository.ContactRepository;
import com.ledgerflow.service.AttachmentService;
import com.ledgerflow.service.BillService;
import com.ledgerflow.service.InvoiceTotals;
import com.ledgerflow.service.PaymentService;
import com.ledgerflow.web.dto.AttachmentResponse;
import com.ledgerflow.web.dto.BillDetailResponse;
import com.ledgerflow.web.dto.BillRequest;
import com.ledgerflow.web.dto.BillResponse;
import com.ledgerflow.web.dto.PagedResponse;
import com.ledgerflow.web.dto.VoidBillRequest;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
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
@RequestMapping("/bills")
public class BillController {

    private static final String ENTITY_TYPE = "BILL";

    private static final Set<String> SORTABLE = Set.of("billDate", "dueDate", "billNumber", "createdAt", "updatedAt");

    private final BillService billService;
    private final AttachmentService attachmentService;
    private final PaymentService paymentService;
    private final ContactRepository contactRepository;

    public BillController(
            BillService billService,
            AttachmentService attachmentService,
            PaymentService paymentService,
            ContactRepository contactRepository) {
        this.billService = billService;
        this.attachmentService = attachmentService;
        this.paymentService = paymentService;
        this.contactRepository = contactRepository;
    }

    @GetMapping
    public PagedResponse<BillResponse> listBills(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) BillStatus status,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {

        Pageable sorted = SortWhitelist.apply(pageable, SORTABLE, Sort.by("billDate").descending());
        Page<Bill> page = billService.search(q, status, sorted);
        return PagedResponse.of(page.getContent().stream().map(this::toResponse).toList(), page);
    }

    @GetMapping("/{id}")
    public BillDetailResponse getBill(@PathVariable Long id) {
        return toDetailResponse(billService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BillDetailResponse createBill(@Valid @RequestBody BillRequest request) {
        return toDetailResponse(billService.create(request.toDraft()));
    }

    @PutMapping("/{id}")
    public BillDetailResponse updateBill(@PathVariable Long id, @Valid @RequestBody BillRequest request) {
        return toDetailResponse(billService.update(id, request.toDraft()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteBill(@PathVariable Long id) {
        billService.delete(id);
    }

    @PostMapping("/{id}/post")
    public BillDetailResponse postBill(@PathVariable Long id) {
        return toDetailResponse(billService.post(id));
    }

    @PostMapping("/{id}/void")
    public BillDetailResponse voidBill(@PathVariable Long id, @RequestBody(required = false) VoidBillRequest request) {
        String reason = request == null ? null : request.reason();
        return toDetailResponse(billService.voidBill(id, reason));
    }

    @GetMapping("/{id}/attachments")
    public List<AttachmentResponse> listAttachments(@PathVariable Long id) {
        return attachmentService.list(ENTITY_TYPE, id).stream().map(AttachmentResponse::from).toList();
    }

    @PostMapping("/{id}/attachments")
    @ResponseStatus(HttpStatus.CREATED)
    public AttachmentResponse uploadAttachment(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        billService.get(id); // 404s cleanly if the bill does not exist, before touching storage
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

    private BillResponse toResponse(Bill bill) {
        List<BillLine> lines = billService.getLines(bill.getId());
        InvoiceTotals totals = billService.totalsFor(lines);
        BigDecimal amountPaid = paymentService.amountPaidFor(DocumentType.BILL, bill.getId());
        return BillResponse.from(bill, contactName(bill.getContactId()), totals, amountPaid);
    }

    private BillDetailResponse toDetailResponse(Bill bill) {
        List<BillLine> lines = billService.getLines(bill.getId());
        InvoiceTotals totals = billService.totalsFor(lines);
        BigDecimal amountPaid = paymentService.amountPaidFor(DocumentType.BILL, bill.getId());
        return BillDetailResponse.from(bill, contactName(bill.getContactId()), lines, totals, amountPaid);
    }

    private String contactName(Long contactId) {
        return contactRepository
                .findById(contactId)
                .map(Contact::getName)
                .orElseThrow(() -> new NoSuchElementException("No contact with id " + contactId));
    }
}
