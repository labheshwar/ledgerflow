package com.ledgerflow.tenancy;

import jakarta.persistence.EntityManagerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TenancyConfig {

    /**
     * Replaces Boot's auto-configured JpaTransactionManager so every
     * transaction carries the tenant into the session.
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(EntityManagerFactory entityManagerFactory) {
        OrgAwareJpaTransactionManager manager = new OrgAwareJpaTransactionManager();
        manager.setEntityManagerFactory(entityManagerFactory);
        return manager;
    }
}
