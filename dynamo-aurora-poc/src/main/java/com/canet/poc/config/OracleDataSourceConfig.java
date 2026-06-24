package com.canet.poc.config;

import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManagerFactory;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.DataSourceBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.transaction.PlatformTransactionManager;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

/**
 * Secondary JPA context for RDS Oracle.
 *
 * Activates only when oracle.enabled=true.
 * The primary JPA context (application.properties) points to Aurora PostgreSQL.
 * This creates a completely separate DataSource, EntityManagerFactory, and
 * TransactionManager for Oracle so both can coexist in the same application.
 *
 * This pattern (dual DataSource) is the standard Spring approach for
 * connecting to two different databases simultaneously.
 */
@Configuration
@ConditionalOnProperty(name = "oracle.enabled", havingValue = "true")
@EnableJpaRepositories(
        basePackages = "com.canet.poc.repository.oracle",
        entityManagerFactoryRef = "oracleEntityManagerFactory",
        transactionManagerRef  = "oracleTransactionManager"
)
public class OracleDataSourceConfig {

    @Bean("oracleDataSource")
    @ConfigurationProperties(prefix = "spring.datasource.oracle")
    public DataSource oracleDataSource() {
        return DataSourceBuilder.create().type(HikariDataSource.class).build();
    }

    @Bean("oracleEntityManagerFactory")
    public LocalContainerEntityManagerFactoryBean oracleEntityManagerFactory(
            @Qualifier("oracleDataSource") DataSource dataSource) {

        LocalContainerEntityManagerFactoryBean em = new LocalContainerEntityManagerFactoryBean();
        em.setDataSource(dataSource);
        em.setPackagesToScan("com.canet.poc.repository.oracle");
        em.setPersistenceUnitName("oracle");

        HibernateJpaVendorAdapter adapter = new HibernateJpaVendorAdapter();
        em.setJpaVendorAdapter(adapter);

        Map<String, Object> props = new HashMap<>();
        props.put("hibernate.dialect", "org.hibernate.dialect.OracleDialect");
        props.put("hibernate.hbm2ddl.auto", "validate");
        props.put("hibernate.show_sql", "false");
        em.setJpaPropertyMap(props);

        return em;
    }

    @Bean("oracleTransactionManager")
    public PlatformTransactionManager oracleTransactionManager(
            @Qualifier("oracleEntityManagerFactory") EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    /**
     * Run Flyway Oracle migration on startup.
     * Uses migration-oracle/ DDL (Oracle-compatible SQL, no JSONB).
     */
    @Bean("oracleFlywayMigration")
    public Flyway oracleFlywayMigration(
            @Qualifier("oracleDataSource") DataSource dataSource) {
        Flyway flyway = Flyway.configure()
                .dataSource(dataSource)
                .locations("classpath:db/migration-oracle")
                .table("flyway_schema_history_oracle")
                .load();
        flyway.migrate();
        return flyway;
    }
}
