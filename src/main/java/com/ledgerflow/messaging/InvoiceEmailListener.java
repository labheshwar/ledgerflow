package com.ledgerflow.messaging;

import com.ledgerflow.config.RabbitMQConfig;
import com.ledgerflow.service.InvoiceDeliveryService;
import com.ledgerflow.tenancy.TenantContext;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/** Worker-only, same reason {@link StatementImportListener} is: rendering a PDF and talking to SMTP must not hold an HTTP thread. */
@Component
@Profile("worker")
public class InvoiceEmailListener {

    private final InvoiceDeliveryService invoiceDeliveryService;

    public InvoiceEmailListener(InvoiceDeliveryService invoiceDeliveryService) {
        this.invoiceDeliveryService = invoiceDeliveryService;
    }

    @RabbitListener(queues = RabbitMQConfig.INVOICE_EMAIL_QUEUE, containerFactory = "invoiceEmailListenerContainerFactory")
    public void onInvoiceEmailRequested(InvoiceEmailRequestedEvent event) {
        TenantContext.runAs(
                event.orgId(),
                (Runnable) () -> invoiceDeliveryService.deliverEmail(event.invoiceId(), event.recipientEmail(), event.reminder()));
    }
}
