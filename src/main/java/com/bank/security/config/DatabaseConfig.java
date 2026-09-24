package com.bank.security.config;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.annotation.PreDestroy;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Produces;
import javax.sql.DataSource;
import java.sql.SQLException;

@ApplicationScoped
public class DatabaseConfig {

    private HikariDataSource dataSource;

    @Produces
    @ApplicationScoped
    public DataSource getDataSource() {
        if (this.dataSource == null) {
            HikariConfig config = new HikariConfig();
            
            // H2 Database configuration running in PostgreSQL compatibility mode
            config.setJdbcUrl("jdbc:h2:mem:bankdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
            config.setDriverClassName("org.h2.Driver");
            config.setUsername("sa");
            config.setPassword("");

            // HikariCP Performance Settings
            config.setMaximumPoolSize(10);
            config.setMinimumIdle(2);
            config.setIdleTimeout(300000); // 5 minutes
            config.setConnectionTimeout(20000); // 20 seconds
            config.setMaxLifetime(1800000); // 30 minutes
            config.setPoolName("S52B1-HikariPool");

            // Cache Prepared Statements for H2 Optimization
            config.addDataSourceProperty("cachePrepStmts", "true");
            config.addDataSourceProperty("prepStmtCacheSize", "250");
            config.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            this.dataSource = new HikariDataSource(config);
        }
        return this.dataSource;
    }

    @PreDestroy
    public void close() {
        if (this.dataSource != null && !this.dataSource.isClosed()) {
            this.dataSource.close();
        }
    }
}