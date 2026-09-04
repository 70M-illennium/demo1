package com.fares.demo1.config;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.jdbc.autoconfigure.DataSourceProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;

/**
 * The application talks to two separate MySQL servers:
 *
 * <ul>
 *   <li><b>storage</b> ({@code @Primary}) - the monitor's own history database. Spring
 *       Data JPA repositories and Hibernate {@code ddl-auto} bind here automatically
 *       because it is the primary {@link DataSource}. Keeping it separate means a lock
 *       pile-up or outage on the monitored server cannot stop the monitor from
 *       recording what it sees.</li>
 *   <li><b>target</b> - the database being monitored. Reached only through
 *       {@link #targetJdbcTemplate}, read-only, with no JPA and no schema management.</li>
 * </ul>
 *
 * <p>Declaring any {@code DataSource} bean switches off Spring Boot's datasource
 * auto-configuration, so both datasources are wired explicitly here. Properties come
 * from {@code spring.datasource.*} (storage) and {@code monitor.target.datasource.*}
 * (target) - see {@code application.properties}.
 */
@Configuration
public class DataSourceConfig {

    // ---------- storage: the monitor's own history DB (primary) ----------

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource")
    public DataSourceProperties storageDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @Primary
    @ConfigurationProperties("spring.datasource.hikari")
    public DataSource storageDataSource(
            @Qualifier("storageDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    // ---------- target: the monitored DB (read-only, JdbcTemplate only) ----------

    @Bean
    @ConfigurationProperties("monitor.target.datasource")
    public DataSourceProperties targetDataSourceProperties() {
        return new DataSourceProperties();
    }

    @Bean
    @ConfigurationProperties("monitor.target.datasource.hikari")
    public DataSource targetDataSource(
            @Qualifier("targetDataSourceProperties") DataSourceProperties properties) {
        return properties.initializeDataSourceBuilder().build();
    }

    @Bean
    public JdbcTemplate targetJdbcTemplate(
            @Qualifier("targetDataSource") DataSource targetDataSource) {
        return new JdbcTemplate(targetDataSource);
    }
}
