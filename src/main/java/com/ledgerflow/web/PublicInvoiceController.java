package com.ledgerflow.web;

import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.DocumentType;
import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoicePublicLink;
import com.ledgerflow.repository.ContactRepository;
import com.ledgerflow.repository.InvoicePublicLinkRepository;
import com.ledgerflow.service.InvoiceDeliveryService;
import com.ledgerflow.service.InvoiceService;
import com.ledgerflow.service.InvoiceTotals;
import com.ledgerflow.service.PaymentService;
import com.ledgerflow.tenancy.TenantContext;
import com.ledgerflow.web.dto.InvoiceDetailResponse;
import java.util.NoSuchElementException;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * No JWT, no tenant, no role -- reachable by anyone holding the link. See
 * {@link InvoicePublicLink}'s Javadoc for how a request with no tenant of
 * its own still ends up reading through the normal row-level-secured path:
 * this table resolves the token to an organization first, then every actual
 * read of the invoice runs inside {@code TenantContext.runAs} for that
 * organization, same as any other tenant-scoped read.
 */
@RestController
@RequestMapping("/public/invoices")
public class PublicInvoiceController {

    private final InvoicePublicLinkRepository invoicePublicLinkRepository;
    private final InvoiceService invoiceService;
    private final InvoiceDeliveryService invoiceDeliveryService;
    private final PaymentService paymentService;
    private final ContactRepository contactRepository;

    public PublicInvoiceController(
            InvoicePublicLinkRepository invoicePublicLinkRepository,
            InvoiceService invoiceService,
            InvoiceDeliveryService invoiceDeliveryService,
            PaymentService paymentService,
            ContactRepository contactRepository) {
        this.invoicePublicLinkRepository = invoicePublicLinkRepository;
        this.invoiceService = invoiceService;
        this.invoiceDeliveryService = invoiceDeliveryService;
        this.paymentService = paymentService;
        this.contactRepository = contactRepository;
    }

    @GetMapping("/{token}")
    public InvoiceDetailResponse getPublicInvoice(@PathVariable String token) {
        InvoicePublicLink link = requireLink(token);
        return TenantContext.runAs(link.getOrgId(), () -> {
            Invoice invoice = invoiceService.get(link.getInvoiceId());
            var lines = invoiceService.getLines(invoice.getId());
            InvoiceTotals totals = invoiceService.totalsFor(lines);
            String contactName = contactRepository.findById(invoice.getContactId()).map(Contact::getName).orElse("Customer");
            var amountPaid = paymentService.amountPaidFor(DocumentType.INVOICE, invoice.getId());
            return InvoiceDetailResponse.from(invoice, contactName, lines, totals, amountPaid);
        });
    }

    @GetMapping("/{token}/pdf")
    public ResponseEntity<byte[]> downloadPublicPdf(@PathVariable String token) {
        InvoicePublicLink link = requireLink(token);
        return TenantContext.runAs(link.getOrgId(), () -> {
            Invoice invoice = invoiceService.get(link.getInvoiceId());
            byte[] pdf = invoiceDeliveryService.renderPdf(invoice.getId());
            String filename = (invoice.getInvoiceNumber() == null ? "invoice" : invoice.getInvoiceNumber()) + ".pdf";
            return ResponseEntity.ok()
                    .contentType(MediaType.APPLICATION_PDF)
                    .header(
                            HttpHeaders.CONTENT_DISPOSITION,
                            ContentDisposition.attachment().filename(filename).build().toString())
                    .body(pdf);
        });
    }

    private InvoicePublicLink requireLink(String token) {
        return invoicePublicLinkRepository
                .findById(token)
                .orElseThrow(() -> new NoSuchElementException("No invoice link for this token"));
    }
}
