package com.ledgerflow.service;

import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoiceLine;
import com.ledgerflow.domain.InvoicePublicLink;
import com.ledgerflow.domain.InvoiceStatus;
import com.ledgerflow.exception.InvoiceException;
import com.ledgerflow.messaging.InvoiceEmailProducer;
import com.ledgerflow.repository.ContactRepository;
import com.ledgerflow.repository.InvoicePublicLinkRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.security.SecureRandom;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Everything about an invoice that is not the ledger: the PDF it renders to,
 * the unguessable link a customer with no account can open, and the email
 * that carries both.
 */
@Service
public class InvoiceDeliveryService {

    private static final SecureRandom RANDOM = new SecureRandom();

    private final InvoiceService invoiceService;
    private final InvoicePublicLinkRepository invoicePublicLinkRepository;
    private final ContactRepository contactRepository;
    private final OrganizationService organizationService;
    private final InvoicePdfRenderer pdfRenderer;
    private final InvoiceMailService mailService;
    private final InvoiceEmailProducer emailProducer;
    private final String publicBaseUrl;

    public InvoiceDeliveryService(
            InvoiceService invoiceService,
            InvoicePublicLinkRepository invoicePublicLinkRepository,
            ContactRepository contactRepository,
            OrganizationService organizationService,
            InvoicePdfRenderer pdfRenderer,
            InvoiceMailService mailService,
            InvoiceEmailProducer emailProducer,
            @Value("${ledgerflow.public-base-url}") String publicBaseUrl) {
        this.invoiceService = invoiceService;
        this.invoicePublicLinkRepository = invoicePublicLinkRepository;
        this.contactRepository = contactRepository;
        this.organizationService = organizationService;
        this.pdfRenderer = pdfRenderer;
        this.mailService = mailService;
        this.emailProducer = emailProducer;
        this.publicBaseUrl = publicBaseUrl;
    }

    public byte[] renderPdf(Long invoiceId) {
        Invoice invoice = invoiceService.get(invoiceId);
        List<InvoiceLine> lines = invoiceService.getLines(invoiceId);
        InvoiceTotals totals = invoiceService.totalsFor(lines);
        return pdfRenderer.render(invoice, lines, totals, contactNameFor(invoice), organizationService.current().getName());
    }

    /** Finds or creates the one public link an invoice has -- a fresh token the first time, the same one after. */
    @Transactional
    public String ensurePublicLink(Long invoiceId) {
        Invoice invoice = invoiceService.get(invoiceId);
        if (invoice.getStatus() == InvoiceStatus.DRAFT) {
            throw new InvoiceException("INVOICE_NOT_SENT_YET", "A draft invoice has nothing to share yet");
        }
        return invoicePublicLinkRepository
                .findByInvoiceId(invoiceId)
                .map(InvoicePublicLink::getToken)
                .orElseGet(() -> createLink(invoice));
    }

    private String createLink(Invoice invoice) {
        try {
            InvoicePublicLink link = new InvoicePublicLink();
            link.setToken(generateToken());
            link.setOrgId(invoice.getOrgId());
            link.setInvoiceId(invoice.getId());
            link.setCreatedAt(OffsetDateTime.now());
            return invoicePublicLinkRepository.save(link).getToken();
        } catch (DataIntegrityViolationException e) {
            // Lost a race with a concurrent request creating the same
            // invoice's link -- their token is just as valid as the one this
            // request would have made.
            return invoicePublicLinkRepository
                    .findByInvoiceId(invoice.getId())
                    .map(InvoicePublicLink::getToken)
                    .orElseThrow(() -> e);
        }
    }

    public String publicUrlFor(String token) {
        return publicBaseUrl + "/public/invoices/" + token;
    }

    /** Queues the send; the worker renders the PDF and delivers it, so a slow SMTP server never holds an HTTP thread. */
    public void requestEmail(Long invoiceId, String recipientEmail, boolean reminder) {
        Invoice invoice = invoiceService.get(invoiceId);
        if (invoice.getStatus() != InvoiceStatus.SENT) {
            throw new InvoiceException("INVOICE_NOT_SENT_YET", "Only a sent invoice can be emailed");
        }
        emailProducer.publish(TenantContext.require(), invoiceId, recipientEmail, reminder);
    }

    /** Called from the worker listener, with the tenant already established. */
    public void deliverEmail(Long invoiceId, String recipientEmail, boolean reminder) {
        Invoice invoice = invoiceService.get(invoiceId);
        byte[] pdf = renderPdf(invoiceId);
        String url = publicUrlFor(ensurePublicLink(invoiceId));
        mailService.sendInvoice(invoice, pdf, recipientEmail, url, reminder);
    }

    private String contactNameFor(Invoice invoice) {
        return contactRepository.findById(invoice.getContactId()).map(Contact::getName).orElse("Customer");
    }

    private static String generateToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
