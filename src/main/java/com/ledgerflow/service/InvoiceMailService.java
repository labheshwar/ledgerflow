package com.ledgerflow.service;

import com.ledgerflow.domain.Invoice;
import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Service;

/** Sends the one email this application sends: an invoice, with its PDF attached and a link to view it online. */
@Service
public class InvoiceMailService {

    private final JavaMailSender mailSender;

    public InvoiceMailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    public void sendInvoice(Invoice invoice, byte[] pdf, String recipientEmail, String publicUrl, boolean reminder) {
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
            helper.setTo(recipientEmail);
            helper.setSubject(
                    reminder
                            ? "Reminder: invoice %s is overdue".formatted(invoice.getInvoiceNumber())
                            : "Invoice %s".formatted(invoice.getInvoiceNumber()));
            helper.setText(body(invoice, publicUrl, reminder), false);
            helper.addAttachment(invoice.getInvoiceNumber() + ".pdf", new ByteArrayResource(pdf), "application/pdf");
            mailSender.send(message);
        } catch (MessagingException e) {
            throw new IllegalStateException("Could not build the email for invoice " + invoice.getId(), e);
        }
    }

    private String body(Invoice invoice, String publicUrl, boolean reminder) {
        String lead = reminder
                ? "This is a reminder that invoice %s, due %s, is now overdue.".formatted(
                        invoice.getInvoiceNumber(), invoice.getDueDate())
                : "Invoice %s is attached as a PDF.".formatted(invoice.getInvoiceNumber());
        return lead + "\n\nYou can also view it online at:\n" + publicUrl + "\n";
    }
}
