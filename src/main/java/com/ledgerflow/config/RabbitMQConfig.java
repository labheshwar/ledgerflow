package com.ledgerflow.config;

import com.ledgerflow.messaging.InvoiceEmailFailureRecoverer;
import com.ledgerflow.messaging.ReconciliationFailureRecoverer;
import com.ledgerflow.messaging.StatementImportFailureRecoverer;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMQConfig {

    public static final String EXCHANGE = "reconciliation.exchange";
    public static final String QUEUE = "reconciliation.queue";
    public static final String ROUTING_KEY = "reconciliation.trigger";

    public static final String DLX = "reconciliation.dlx";
    public static final String DLQ = "reconciliation.dlq";
    public static final String DLQ_ROUTING_KEY = "reconciliation.trigger.dlq";

    public static final String INVOICE_EMAIL_EXCHANGE = "invoice-email.exchange";
    public static final String INVOICE_EMAIL_QUEUE = "invoice-email.queue";
    public static final String INVOICE_EMAIL_ROUTING_KEY = "invoice-email.send";

    public static final String INVOICE_EMAIL_DLX = "invoice-email.dlx";
    public static final String INVOICE_EMAIL_DLQ = "invoice-email.dlq";
    public static final String INVOICE_EMAIL_DLQ_ROUTING_KEY = "invoice-email.send.dlq";

    public static final String STATEMENT_IMPORT_EXCHANGE = "statement-import.exchange";
    public static final String STATEMENT_IMPORT_QUEUE = "statement-import.queue";
    public static final String STATEMENT_IMPORT_ROUTING_KEY = "statement-import.preview";

    public static final String STATEMENT_IMPORT_DLX = "statement-import.dlx";
    public static final String STATEMENT_IMPORT_DLQ = "statement-import.dlq";
    public static final String STATEMENT_IMPORT_DLQ_ROUTING_KEY = "statement-import.preview.dlq";

    @Bean
    public DirectExchange reconciliationExchange() {
        return new DirectExchange(EXCHANGE);
    }

    @Bean
    public Queue reconciliationQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding reconciliationBinding(Queue reconciliationQueue, DirectExchange reconciliationExchange) {
        return BindingBuilder.bind(reconciliationQueue).to(reconciliationExchange).with(ROUTING_KEY);
    }

    @Bean
    public DirectExchange reconciliationDlx() {
        return new DirectExchange(DLX);
    }

    @Bean
    public Queue reconciliationDlq() {
        return QueueBuilder.durable(DLQ).build();
    }

    @Bean
    public Binding reconciliationDlqBinding(Queue reconciliationDlq, DirectExchange reconciliationDlx) {
        return BindingBuilder.bind(reconciliationDlq).to(reconciliationDlx).with(DLQ_ROUTING_KEY);
    }

    @Bean
    public DirectExchange invoiceEmailExchange() {
        return new DirectExchange(INVOICE_EMAIL_EXCHANGE);
    }

    @Bean
    public Queue invoiceEmailQueue() {
        return QueueBuilder.durable(INVOICE_EMAIL_QUEUE).build();
    }

    @Bean
    public Binding invoiceEmailBinding(Queue invoiceEmailQueue, DirectExchange invoiceEmailExchange) {
        return BindingBuilder.bind(invoiceEmailQueue).to(invoiceEmailExchange).with(INVOICE_EMAIL_ROUTING_KEY);
    }

    @Bean
    public DirectExchange invoiceEmailDlx() {
        return new DirectExchange(INVOICE_EMAIL_DLX);
    }

    @Bean
    public Queue invoiceEmailDlq() {
        return QueueBuilder.durable(INVOICE_EMAIL_DLQ).build();
    }

    @Bean
    public Binding invoiceEmailDlqBinding(Queue invoiceEmailDlq, DirectExchange invoiceEmailDlx) {
        return BindingBuilder.bind(invoiceEmailDlq).to(invoiceEmailDlx).with(INVOICE_EMAIL_DLQ_ROUTING_KEY);
    }

    @Bean
    public DirectExchange statementImportExchange() {
        return new DirectExchange(STATEMENT_IMPORT_EXCHANGE);
    }

    @Bean
    public Queue statementImportQueue() {
        return QueueBuilder.durable(STATEMENT_IMPORT_QUEUE).build();
    }

    @Bean
    public Binding statementImportBinding(Queue statementImportQueue, DirectExchange statementImportExchange) {
        return BindingBuilder.bind(statementImportQueue).to(statementImportExchange).with(STATEMENT_IMPORT_ROUTING_KEY);
    }

    @Bean
    public DirectExchange statementImportDlx() {
        return new DirectExchange(STATEMENT_IMPORT_DLX);
    }

    @Bean
    public Queue statementImportDlq() {
        return QueueBuilder.durable(STATEMENT_IMPORT_DLQ).build();
    }

    @Bean
    public Binding statementImportDlqBinding(Queue statementImportDlq, DirectExchange statementImportDlx) {
        return BindingBuilder.bind(statementImportDlq).to(statementImportDlx).with(STATEMENT_IMPORT_DLQ_ROUTING_KEY);
    }

    @Bean
    public Jackson2JsonMessageConverter rabbitMessageConverter() {
        return new Jackson2JsonMessageConverter();
    }

    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory, Jackson2JsonMessageConverter converter) {
        RabbitTemplate template = new RabbitTemplate(connectionFactory);
        template.setMessageConverter(converter);
        return template;
    }

    /**
     * Named to match Spring Boot's default so every @RabbitListener picks it
     * up without an explicit containerFactory attribute. A failed listener
     * invocation is retried in-process (3 attempts, exponential backoff);
     * once exhausted, ReconciliationFailureRecoverer marks the batch FAILED
     * and republishes the message to the dead-letter queue instead of
     * looping forever.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            ReconciliationFailureRecoverer recoverer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000L, 2.0, 10_000L)
                .recoverer(recoverer)
                .build());
        return factory;
    }

    /**
     * A second factory rather than reusing the one above: that one's advice
     * chain is wired to ReconciliationFailureRecoverer specifically, which
     * would try to deserialize an invoice-email message as a
     * ReconciliationRequestedEvent and republish a genuinely failed send to
     * the wrong dead-letter queue entirely.
     */
    @Bean
    public SimpleRabbitListenerContainerFactory invoiceEmailListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            InvoiceEmailFailureRecoverer recoverer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000L, 2.0, 10_000L)
                .recoverer(recoverer)
                .build());
        return factory;
    }

    /** A third factory, same reasoning as the invoice-email one above: its own failure recoverer, its own dead-letter queue. */
    @Bean
    public SimpleRabbitListenerContainerFactory statementImportListenerContainerFactory(
            ConnectionFactory connectionFactory,
            Jackson2JsonMessageConverter converter,
            StatementImportFailureRecoverer recoverer) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(converter);
        factory.setAdviceChain(RetryInterceptorBuilder.stateless()
                .maxAttempts(3)
                .backOffOptions(1000L, 2.0, 10_000L)
                .recoverer(recoverer)
                .build());
        return factory;
    }
}
