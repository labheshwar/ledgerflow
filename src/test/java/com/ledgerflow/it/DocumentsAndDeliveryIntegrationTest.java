package com.ledgerflow.it;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.ledgerflow.domain.Attachment;
import com.ledgerflow.domain.Contact;
import com.ledgerflow.domain.ContactType;
import com.ledgerflow.domain.Invoice;
import com.ledgerflow.exception.InvoiceException;
import com.ledgerflow.service.AttachmentService;
import com.ledgerflow.service.ContactDraft;
import com.ledgerflow.service.ContactService;
import com.ledgerflow.service.InvoiceDeliveryService;
import com.ledgerflow.service.InvoiceDraft;
import com.ledgerflow.service.InvoiceLineDraft;
import com.ledgerflow.service.InvoiceService;
import java.io.InputStream;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

/**
 * PDFs, object storage and email, exercised against real MinIO and MailHog
 * rather than mocks -- a mocked S3 client would happily "upload" bytes that
 * a real bucket policy or a real network hiccup could still reject, and a
 * mocked mail sender proves nothing about whether the message that leaves
 * this process is one an actual SMTP server accepts.
 */
class DocumentsAndDeliveryIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private InvoiceService invoiceService;

    @Autowired
    private InvoiceDeliveryService invoiceDeliveryService;

    @Autowired
    private AttachmentService attachmentService;

    @Autowired
    private ContactService contactService;


    @Test
    void rendersAPdfThatStartsWithTheFormatsOwnMagicBytes() {
        Invoice invoice = draftInvoice();

        byte[] pdf = invoiceDeliveryService.renderPdf(invoice.getId());

        assertThat(pdf.length).isGreaterThan(100);
        assertThat(new String(pdf, 0, 5, StandardCharsets.US_ASCII)).isEqualTo("%PDF-");
    }

    @Test
    void aPublicLinkIsTheSameTokenEveryTimeItIsRequested() {
        Invoice invoice = invoiceService.send(draftInvoice().getId());

        String first = invoiceDeliveryService.ensurePublicLink(invoice.getId());
        String second = invoiceDeliveryService.ensurePublicLink(invoice.getId());

        assertThat(second).isEqualTo(first);
        assertThat(invoiceDeliveryService.publicUrlFor(first)).contains(first);
    }

    @Test
    void aDraftInvoiceHasNoPublicLinkYet() {
        Invoice draft = draftInvoice();

        assertThatThrownBy(() -> invoiceDeliveryService.ensurePublicLink(draft.getId()))
                .isInstanceOf(InvoiceException.class)
                .satisfies(e -> assertThat(((InvoiceException) e).getCode()).isEqualTo("INVOICE_NOT_SENT_YET"));
    }

    @Test
    void onlyASentInvoiceCanBeEmailed() {
        Invoice draft = draftInvoice();

        assertThatThrownBy(() -> invoiceDeliveryService.requestEmail(draft.getId(), "customer@example.test", false))
                .isInstanceOf(InvoiceException.class)
                .satisfies(e -> assertThat(((InvoiceException) e).getCode()).isEqualTo("INVOICE_NOT_SENT_YET"));
    }

    @Test
    void sendingAnInvoiceEmailActuallyArrivesAtTheSmtpServer() {
        Invoice invoice = invoiceService.send(draftInvoice().getId());
        String recipient = "delivery-test-" + UUID.randomUUID() + "@example.test";

        invoiceDeliveryService.requestEmail(invoice.getId(), recipient, false);

        String body = awaitMailhogMessageTo(recipient);
        assertThat(body).contains(invoice.getInvoiceNumber());
        // The PDF's own filename, which only appears if it was actually
        // attached rather than the email going out as bare text.
        assertThat(body).contains(invoice.getInvoiceNumber() + ".pdf");
    }

    @Test
    void aReminderEmailIsMarkedAsSuchInItsSubject() {
        Invoice invoice = invoiceService.send(draftInvoice().getId());
        String recipient = "reminder-test-" + UUID.randomUUID() + "@example.test";

        invoiceDeliveryService.requestEmail(invoice.getId(), recipient, true);

        String body = awaitMailhogMessageTo(recipient);
        assertThat(body.toLowerCase()).contains("reminder");
    }

    @Test
    void uploadingListingDownloadingAndDeletingAnAttachmentRoundTrips() {
        Invoice invoice = draftInvoice();
        byte[] content = "a receipt, in spirit if not in format".getBytes(StandardCharsets.UTF_8);

        Attachment uploaded = attachmentService.upload("INVOICE", invoice.getId(), "receipt.txt", "text/plain", content);

        List<Attachment> listed = attachmentService.list("INVOICE", invoice.getId());
        assertThat(listed).extracting(Attachment::getId).contains(uploaded.getId());

        byte[] downloaded = attachmentService.download(uploaded.getId());
        assertThat(downloaded).isEqualTo(content);

        attachmentService.delete(uploaded.getId());
        assertThat(attachmentService.list("INVOICE", invoice.getId())).extracting(Attachment::getId).doesNotContain(uploaded.getId());
    }

    private Invoice draftInvoice() {
        Contact customer = contactService.create(new ContactDraft(
                ContactType.CUSTOMER, "Delivery Test Customer " + UUID.randomUUID(), null, null, null, null, null,
                null, null, null, null, null));
        return invoiceService.create(new InvoiceDraft(
                customer.getId(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 31),
                null,
                List.of(new InvoiceLineDraft(null, "Line", BigDecimal.ONE, new BigDecimal("42.00"), null))));
    }

    /**
     * Polls MailHog's own API rather than trusting the send call -- see this
     * class's own Javadoc. Plain {@link java.net.HttpURLConnection} rather
     * than {@code java.net.http.HttpClient}: the latter's selector setup
     * hits this host's own loopback-connection NIO bug, the same one
     * AbstractIntegrationTest's Javadoc already documents.
     */
    private String awaitMailhogMessageTo(String recipient) {
        String[] found = new String[1];
        awaitCondition(() -> {
            try {
                var connection = (java.net.HttpURLConnection) URI.create(mailhogApiUrl()).toURL().openConnection();
                connection.setRequestMethod("GET");
                try (InputStream in = connection.getInputStream()) {
                    String body = new String(in.readAllBytes(), StandardCharsets.UTF_8);
                    if (body.contains(recipient)) {
                        found[0] = body;
                        return true;
                    }
                }
            } catch (Exception e) {
                // Transient during startup; awaitCondition keeps polling.
            }
            return false;
        });
        return found[0];
    }
}
