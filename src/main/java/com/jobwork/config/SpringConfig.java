package com.jobwork.config;

import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.orm.jpa.JpaTransactionManager;
import org.springframework.orm.jpa.LocalContainerEntityManagerFactoryBean;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import javax.sql.DataSource;
import jakarta.persistence.EntityManagerFactory;
import java.util.Properties;

@Configuration
@EnableTransactionManagement
@EntityScan(basePackages = "com.jobwork.domain")
@EnableJpaRepositories(basePackages = "com.jobwork.repository")
public class SpringConfig {

    /**
     * EntityManagerFactory wired manually so we can set
     * Hibernate properties explicitly — more transparent
     * than relying solely on application.properties.
     *
     * Spring Boot auto-config will also work if you prefer;
     * this class is optional when using spring-boot-starter-data-jpa
     * with application.properties fully configured.
     */
    @Bean
    public LocalContainerEntityManagerFactoryBean entityManagerFactory(
            DataSource dataSource) {

        HibernateJpaVendorAdapter adapter =
                new HibernateJpaVendorAdapter();
        adapter.setShowSql(true);
        adapter.setGenerateDdl(true);   // honours ddl-auto property

        LocalContainerEntityManagerFactoryBean factory =
                new LocalContainerEntityManagerFactoryBean();
        factory.setDataSource(dataSource);
        factory.setJpaVendorAdapter(adapter);
        factory.setPackagesToScan("com.jobwork.domain");
        factory.setJpaProperties(hibernateProperties());
        return factory;
    }

    @Bean
    public JpaTransactionManager transactionManager(
            EntityManagerFactory emf) {
        return new JpaTransactionManager(emf);
    }

    private Properties hibernateProperties() {
        Properties p = new Properties();
        p.setProperty("hibernate.hbm2ddl.auto",        "update");
        p.setProperty("hibernate.dialect",
                "org.hibernate.dialect.MySQL8Dialect");
        p.setProperty("hibernate.format_sql",          "true");
        p.setProperty("hibernate.use_sql_comments",     "true");
        // Connection pool — Hikari is Spring Boot default
        //p.setProperty("hibernate.connection.provider_class",
         //       "org.hibernate.hikaricp.internal"
          //              + ".HikariCPConnectionProvider");
        return p;
    }
}