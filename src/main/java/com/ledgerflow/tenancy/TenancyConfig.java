package com.ledgerflow.tenancy;

import jakarta.persistence.EntityManagerFactory;
import javax.sql.DataSource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.transaction.PlatformTransactionManager;

@Configuration
public class TenancyConfig {

    /**
     * Replaces Boot's auto-configured JpaTransactionManager so every
     * transaction carries the tenant into the session.
     *
     * Handing it the DataSource is not optional decoration. Without it the
     * manager binds only the EntityManager, so a JdbcTemplate in the same
     * method opens a second, independent connection -- one that is outside
     * the transaction and, worse, has no tenant set, so row-level security
     * filters everything it touches. With the DataSource set, JPA exposes
     * its connection and plain JDBC joins the same transaction on the same
     * connection. The outbox write depends on exactly this.
     */
    @Bean
    @Primary
    public PlatformTransactionManager transactionManager(
            EntityManagerFactory entityManagerFactory, DataSource dataSource) {
        OrgAwareJpaTransactionManager manager = new OrgAwareJpaTransactionManager();
        manager.setEntityManagerFactory(entityManagerFactory);
        manager.setDataSource(dataSource);
        return manager;
    }
}
