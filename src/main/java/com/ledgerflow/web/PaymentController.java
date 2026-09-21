package com.ledgerflow.web;

import com.ledgerflow.domain.BankAccount;
import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.Payment;
import com.ledgerflow.domain.PaymentDirection;
import com.ledgerflow.repository.BankAccountRepository;
import com.ledgerflow.repository.ContactRepository;
import com.ledgerflow.service.PaymentService;
import com.ledgerflow.web.dto.OpenDocumentResponse;
import com.ledgerflow.web.dto.PagedResponse;
import com.ledgerflow.web.dto.PaymentRequest;
import com.ledgerflow.web.dto.PaymentResponse;
import com.ledgerflow.web.dto.VoidPaymentRequest;
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
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/payments")
public class PaymentController {

    private static final Set<String> SORTABLE = Set.of("paymentDate", "createdAt");

    private final PaymentService paymentService;
    private final ContactRepository contactRepository;
    private final BankAccountRepository bankAccountRepository;

    public PaymentController(
            PaymentService paymentService, ContactRepository contactRepository, BankAccountRepository bankAccountRepository) {
        this.paymentService = paymentService;
        this.contactRepository = contactRepository;
        this.bankAccountRepository = bankAccountRepository;
    }

    @GetMapping
    public PagedResponse<PaymentResponse> listPayments(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) PaymentDirection direction,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {

        Pageable sorted = SortWhitelist.apply(pageable, SORTABLE, Sort.by("paymentDate").descending());
        Page<Payment> page = paymentService.search(q, direction, sorted);
        return PagedResponse.of(page.getContent().stream().map(this::toResponse).toList(), page);
    }

    @GetMapping("/{id}")
    public PaymentResponse getPayment(@PathVariable Long id) {
        return toResponse(paymentService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PaymentResponse createPayment(@Valid @RequestBody PaymentRequest request) {
        return toResponse(paymentService.create(request.toDraft()));
    }

    @PostMapping("/{id}/void")
    public PaymentResponse voidPayment(@PathVariable Long id, @RequestBody(required = false) VoidPaymentRequest request) {
        String reason = request == null ? null : request.reason();
        return toResponse(paymentService.voidPayment(id, reason));
    }

    /** What a payment in this direction, for this contact, could still be allocated against. */
    @GetMapping("/open-documents")
    public List<OpenDocumentResponse> openDocuments(
            @RequestParam Long contactId, @RequestParam PaymentDirection direction) {
        return paymentService.openDocumentsFor(contactId, direction).stream().map(OpenDocumentResponse::from).toList();
    }

    private PaymentResponse toResponse(Payment payment) {
        return PaymentResponse.from(
                payment,
                contactName(payment.getContactId()),
                bankAccountName(payment.getBankAccountId()),
                paymentService.getAllocations(payment.getId()));
    }

    private String contactName(Long contactId) {
        return contactRepository
                .findById(contactId)
                .map(Contact::getName)
                .orElseThrow(() -> new NoSuchElementException("No contact with id " + contactId));
    }

    private String bankAccountName(Long bankAccountId) {
        return bankAccountId == null ? null : bankAccountRepository.findById(bankAccountId).map(BankAccount::getName).orElse(null);
    }
}
