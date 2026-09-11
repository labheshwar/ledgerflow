package com.ledgerflow.config;

import com.ledgerflow.messaging.ReconciliationFailureRecoverer;
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
}
