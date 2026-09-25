package com.bank.security.util;

import com.bank.security.config.DatabaseConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseSeederIT {

    private static HikariDataSource dataSource;
    private static DatabaseSeeder seeder;

    @BeforeAll
    static void setUp() throws SQLException {
        DatabaseConfig config = new DatabaseConfig();
        dataSource = (HikariDataSource) config.getDataSource();
        seeder = new DatabaseSeeder(dataSource);
        seeder.initializeSchema();
    }

    @AfterAll
    static void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @BeforeEach
    void cleanDatabase() throws SQLException {
        seeder.truncateAll();
    }

    @Test
    @DisplayName("Integration Test: High volume batch seeding and truncation over HikariCP connection pool")
    void testHighVolumeBatchSeeding() throws SQLException {
        int userCount = 50;
        int accountsPerUser = 2;
        int ledgerEntriesPerAccount = 10;
        int auditLogCount = 100;

        DatabaseSeeder.SeederResult result = seeder.seedAll(
                userCount, accountsPerUser, ledgerEntriesPerAccount, auditLogCount);

        assertEquals(userCount, result.getUserCount());
        assertEquals(userCount * accountsPerUser, result.getAccountCount());
        assertEquals(userCount * accountsPerUser * ledgerEntriesPerAccount, result.getLedgerEntryCount());
        assertEquals(auditLogCount, result.getAuditLogCount());

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement()) {

            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
                assertTrue(rs.next());
                assertEquals(50, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM accounts")) {
                assertTrue(rs.next());
                assertEquals(100, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM ledger_entries")) {
                assertTrue(rs.next());
                assertEquals(1000, rs.getInt(1));
            }
            try (ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM audit_logs")) {
                assertTrue(rs.next());
                assertEquals(100, rs.getInt(1));
            }
        }

        // Verify clean truncation
        seeder.truncateAll();

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM users")) {
            assertTrue(rs.next());
            assertEquals(0, rs.getInt(1));
        }
    }
}
