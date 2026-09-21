package com.ledgerflow.service;

import com.ledgerflow.domain.Invoice;
import com.ledgerflow.domain.InvoiceLine;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * Renders an invoice to a PDF -- one HTML template, not a second layout kept
 * in sync with the web view by hand. openhtmltopdf takes plain XHTML and
 * inline CSS and lays it out itself; nothing here talks to a browser.
 */
@Service
public class InvoicePdfRenderer {

    public byte[] render(Invoice invoice, List<InvoiceLine> lines, InvoiceTotals totals, String contactName, String orgName) {
        String html = buildHtml(invoice, lines, totals, contactName, orgName);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        PdfRendererBuilder builder = new PdfRendererBuilder();
        builder.useFastMode();
        builder.withHtmlContent(html, null);
        builder.toStream(out);
        try {
            builder.run();
        } catch (Exception e) {
            throw new IllegalStateException("Could not render invoice " + invoice.getId() + " to PDF", e);
        }
        return out.toByteArray();
    }

    private String buildHtml(Invoice invoice, List<InvoiceLine> lines, InvoiceTotals totals, String contactName, String orgName) {
        StringBuilder rows = new StringBuilder();
        for (int i = 0; i < lines.size(); i++) {
            InvoiceLine line = lines.get(i);
            InvoiceLineTotal lineTotal = totals.lines().get(i);
            rows.append(
                    """
                    <tr>
                      <td>%s</td>
                      <td class="num">%s</td>
                      <td class="num">%s</td>
                      <td class="num">%s</td>
                      <td class="num">%s</td>
                    </tr>
                    """
                            .formatted(
                                    escape(line.getDescription()),
                                    line.getQuantity().toPlainString(),
                                    money(line.getUnitPrice()),
                                    money(lineTotal.taxAmount()),
                                    money(lineTotal.lineTotal())));
        }

        return """
                <html>
                <head>
                <style>
                  body { font-family: sans-serif; font-size: 12px; color: #1a1a1a; }
                  h1 { font-size: 20px; margin-bottom: 2px; }
                  .muted { color: #666; }
                  table { width: 100%%; border-collapse: collapse; margin-top: 20px; }
                  th, td { padding: 6px 8px; border-bottom: 1px solid #ddd; text-align: left; }
                  .num { text-align: right; }
                  .totals { width: 260px; margin-left: auto; margin-top: 12px; }
                  .totals td { border-bottom: none; padding: 3px 8px; }
                  .grand { font-weight: bold; border-top: 1px solid #333; }
                </style>
                </head>
                <body>
                  <h1>%s</h1>
                  <div class="muted">Invoice %s</div>
                  <p>
                    <strong>Bill to:</strong> %s<br/>
                    <strong>Issued:</strong> %s &#160; <strong>Due:</strong> %s
                  </p>
                  <table>
                    <tr><th>Description</th><th class="num">Qty</th><th class="num">Unit price</th><th class="num">Tax</th><th class="num">Line total</th></tr>
                    %s
                  </table>
                  <table class="totals">
                    <tr><td>Subtotal</td><td class="num">%s</td></tr>
                    <tr><td>Tax</td><td class="num">%s</td></tr>
                    <tr class="grand"><td>Total</td><td class="num">%s</td></tr>
                  </table>
                  %s
                </body>
                </html>
                """
                .formatted(
                        escape(orgName),
                        escape(invoice.getInvoiceNumber() == null ? "DRAFT" : invoice.getInvoiceNumber()),
                        escape(contactName),
                        invoice.getIssueDate(),
                        invoice.getDueDate(),
                        rows,
                        money(totals.subtotal()),
                        money(totals.taxTotal()),
                        money(totals.grandTotal()),
                        invoice.getNotes() == null
                                ? ""
                                : "<p class=\"muted\">%s</p>".formatted(escape(invoice.getNotes())));
    }

    private String money(BigDecimal amount) {
        return amount.setScale(2).toPlainString();
    }

    /** No templating engine here, so this is the one thing standing between user text and the markup around it. */
    private String escape(String value) {
        if (value == null) {
            return "";
        }
        return value
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
