package com.bank.security.util;

import com.bank.security.auth.DatabaseLoginModule;
import com.bank.security.config.DatabaseConfig;
import com.bank.security.domain.AccountEntity;
import com.bank.security.domain.AuditLogEntity;
import com.bank.security.domain.LedgerEntry;
import com.bank.security.domain.UserEntity;
import com.zaxxer.hikari.HikariDataSource;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import javax.security.auth.login.FailedLoginException;
import java.security.MessageDigest;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseSeederTest {

    private static HikariDataSource dataSource;
    private static DatabaseSeeder seeder;
    private static EntityManagerFactory emf;

    @BeforeAll
    static void initSuite() throws SQLException {
        DatabaseConfig config = new DatabaseConfig();
        dataSource = (HikariDataSource) config.getDataSource();
        seeder = new DatabaseSeeder(dataSource);
        seeder.initializeSchema();

        java.util.Map<String, String> props = new java.util.HashMap<>();
        props.put("jakarta.persistence.jdbc.url",
                "jdbc:h2:mem:bankdb;DB_CLOSE_DELAY=-1;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE");
        props.put("hibernate.hbm2ddl.auto", "none");
        emf = Persistence.createEntityManagerFactory("bankPU", props);
    }

    @AfterAll
    static void tearDownSuite() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @BeforeEach
    void cleanDatabase() throws SQLException {
        seeder.truncateAll();
    }

    private int countRows(String tableName) throws SQLException {
        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery("SELECT COUNT(*) FROM " + tableName)) {
            assertTrue(rs.next());
            return rs.getInt(1);
        }
    }

    @Test
    @DisplayName("Should initialize H2 schema successfully from schema.sql")
    void testInitializeSchema() throws SQLException {
        seeder.initializeSchema();

        assertEquals(0, countRows("users"));
        assertEquals(0, countRows("user_roles"));
        assertEquals(0, countRows("accounts"));
        assertEquals(0, countRows("ledger_entries"));
        assertEquals(0, countRows("audit_logs"));
    }

    @Test
    @DisplayName("Should seed users and roles in batch using DataFaker")
    void testSeedUsers() throws SQLException {
        int userCount = 15;
        List<String> usernames = seeder.seedUsers(userCount);

        assertEquals(userCount, usernames.size());
        assertEquals(userCount, countRows("users"));

        // Verify roles seeded
        int roleCount = countRows("user_roles");
        assertTrue(roleCount >= userCount, "Each user must have at least ROLE_USER");

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT username, email, first_name, last_name, password_hash, salt FROM users")) {
            int checked = 0;
            while (rs.next()) {
                assertNotNull(rs.getString("username"));
                assertNotNull(rs.getString("email"));
                assertNotNull(rs.getString("first_name"));
                assertNotNull(rs.getString("last_name"));

                byte[] hash = rs.getBytes("password_hash");
                byte[] salt = rs.getBytes("salt");
                assertNotNull(hash);
                assertNotNull(salt);
                assertEquals(16, salt.length, "Salt must be 16 bytes");
                assertEquals(32, hash.length, "PBKDF2 256-bit hash must be 32 bytes");

                byte[] expectedHash = DatabaseSeeder.hashPassword(DatabaseSeeder.DEFAULT_PASSWORD, salt);
                assertArrayEquals(expectedHash, hash, "Stored hash must match PBKDF2 derivation");

                checked++;
            }
            assertEquals(userCount, checked);
        }
    }

    @Test
    @DisplayName("Should verify PBKDF2 key derivation and successful authentication with DatabaseLoginModule")
    void testPbkdf2KeyDerivationAndLoginModuleAuth() throws Exception {
        String testPassword = "MySecur3BankingPassword!";
        List<String> usernames = seeder.seedUsers(1, testPassword);
        String seededUsername = usernames.get(0);

        // 1. Verify credentials from database directly
        try (Connection conn = dataSource.getConnection();
                PreparedStatement stmt = conn
                        .prepareStatement("SELECT password_hash, salt FROM users WHERE username = ?")) {
            stmt.setString(1, seededUsername);
            try (ResultSet rs = stmt.executeQuery()) {
                assertTrue(rs.next());
                byte[] storedHash = rs.getBytes("password_hash");
                byte[] storedSalt = rs.getBytes("salt");

                assertEquals(16, storedSalt.length);
                assertEquals(32, storedHash.length);

                byte[] recomputed = DatabaseSeeder.hashPassword(testPassword, storedSalt);
                assertTrue(MessageDigest.isEqual(storedHash, recomputed));
            }
        }

        // 2. Authenticate through DatabaseLoginModule using the seeded credentials
        DatabaseLoginModule loginModule = new DatabaseLoginModule();
        Subject subject = new Subject();
        CallbackHandler validAuthHandler = callbacks -> {
            for (Callback cb : callbacks) {
                if (cb instanceof NameCallback nc) {
                    nc.setName(seededUsername);
                } else if (cb instanceof PasswordCallback pc) {
                    pc.setPassword(testPassword.toCharArray());
                }
            }
        };

        loginModule.initialize(subject, validAuthHandler, new HashMap<>(), new HashMap<>());
        assertTrue(loginModule.login(), "Login should succeed with correct PBKDF2 password");
        assertTrue(loginModule.commit(), "Commit should attach principals to Subject");
        assertFalse(subject.getPrincipals().isEmpty(), "Subject must have authenticated principals");

        // 3. Verify incorrect password fails authentication
        DatabaseLoginModule failedModule = new DatabaseLoginModule();
        Subject failedSubject = new Subject();
        CallbackHandler invalidAuthHandler = callbacks -> {
            for (Callback cb : callbacks) {
                if (cb instanceof NameCallback nc) {
                    nc.setName(seededUsername);
                } else if (cb instanceof PasswordCallback pc) {
                    pc.setPassword("WrongPassword!".toCharArray());
                }
            }
        };

        failedModule.initialize(failedSubject, invalidAuthHandler, new HashMap<>(), new HashMap<>());
        assertThrows(FailedLoginException.class, failedModule::login, "Login must fail with incorrect password");
    }

    @Test
    @DisplayName("Should seed accounts in batch linked to existing users")
    void testSeedAccounts() throws SQLException {
        List<String> usernames = seeder.seedUsers(5);
        int accountsPerUser = 3;

        List<Long> accountIds = seeder.seedAccounts(usernames, accountsPerUser);

        assertEquals(15, accountIds.size());
        assertEquals(15, countRows("accounts"));

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt
                        .executeQuery("SELECT account_number, username, account_type, balance, status FROM accounts")) {
            int checked = 0;
            while (rs.next()) {
                assertTrue(rs.getString("account_number").startsWith("ACCT-"));
                assertTrue(usernames.contains(rs.getString("username")));
                assertNotNull(rs.getString("account_type"));
                assertTrue(rs.getBigDecimal("balance").signum() > 0);
                assertEquals("ACTIVE", rs.getString("status"));
                checked++;
            }
            assertEquals(15, checked);
        }
    }

    @Test
    @DisplayName("Should seed ledger entries in batch tied to accounts")
    void testSeedLedgerEntries() throws SQLException {
        List<String> usernames = seeder.seedUsers(3);
        List<Long> accountIds = seeder.seedAccounts(usernames, 2); // 6 accounts
        int entriesPerAccount = 4;

        seeder.seedLedgerEntries(accountIds, entriesPerAccount);

        int expectedLedgerEntries = accountIds.size() * entriesPerAccount;
        assertEquals(expectedLedgerEntries, countRows("ledger_entries"));

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT transaction_id, entry_type, amount, currency, balance_after, description FROM ledger_entries")) {
            int checked = 0;
            while (rs.next()) {
                assertTrue(rs.getString("transaction_id").startsWith("TX-"));
                assertNotNull(rs.getString("entry_type"));
                assertTrue(rs.getBigDecimal("amount").signum() > 0);
                assertEquals("USD", rs.getString("currency"));
                assertNotNull(rs.getBigDecimal("balance_after"));
                assertNotNull(rs.getString("description"));
                checked++;
            }
            assertEquals(expectedLedgerEntries, checked);
        }
    }

    @Test
    @DisplayName("Should seed audit logs in batch with JSON payload deltas")
    void testSeedAuditLogs() throws SQLException {
        int logCount = 20;
        seeder.seedAuditLogs(logCount);

        assertEquals(logCount, countRows("audit_logs"));

        try (Connection conn = dataSource.getConnection();
                Statement stmt = conn.createStatement();
                ResultSet rs = stmt.executeQuery(
                        "SELECT principal, action, entity_name, ip_address, payload_delta, status, timestamp FROM audit_logs")) {
            int checked = 0;
            while (rs.next()) {
                assertNotNull(rs.getString("principal"));
                assertNotNull(rs.getString("action"));
                assertNotNull(rs.getString("entity_name"));
                assertNotNull(rs.getString("ip_address"));
                assertTrue(rs.getString("payload_delta").contains("action"));
                assertNotNull(rs.getString("status"));
                assertNotNull(rs.getTimestamp("timestamp"));
                checked++;
            }
            assertEquals(logCount, checked);
        }
    }

    @Test
    @DisplayName("Should seed all tables completely via seedAll")
    void testSeedAll() throws SQLException {
        DatabaseSeeder.SeederResult result = seeder.seedAll(10, 2, 5, 25);

        assertEquals(10, result.getUserCount());
        assertEquals(20, result.getAccountCount());
        assertEquals(100, result.getLedgerEntryCount());
        assertEquals(25, result.getAuditLogCount());

        assertEquals(10, countRows("users"));
        assertEquals(20, countRows("accounts"));
        assertEquals(100, countRows("ledger_entries"));
        assertEquals(25, countRows("audit_logs"));
    }

    @Test
    @DisplayName("Should maintain test isolation through truncateAll")
    void testTruncateAllMaintainsIsolation() throws SQLException {
        seeder.seedAll(5, 2, 3, 10);
        assertTrue(countRows("users") > 0);
        assertTrue(countRows("accounts") > 0);
        assertTrue(countRows("ledger_entries") > 0);
        assertTrue(countRows("audit_logs") > 0);

        seeder.truncateAll();

        assertEquals(0, countRows("users"));
        assertEquals(0, countRows("user_roles"));
        assertEquals(0, countRows("accounts"));
        assertEquals(0, countRows("ledger_entries"));
        assertEquals(0, countRows("audit_logs"));

        // Verify we can reseed without primary key / constraint collisions
        seeder.seedAll(3, 1, 2, 5);
        assertEquals(3, countRows("users"));
        assertEquals(3, countRows("accounts"));
        assertEquals(6, countRows("ledger_entries"));
        assertEquals(5, countRows("audit_logs"));
    }

    @Test
    @DisplayName("Should verify JPA EntityManager can read data seeded via JDBC batch statements")
    void testJpaCompatibilityWithSeededData() throws SQLException {
        DatabaseSeeder.SeederResult result = seeder.seedAll(3, 2, 2, 5);

        EntityManager em = emf.createEntityManager();
        try {
            // Find a seeded user through JPA
            String username = result.getUsernames().get(0);
            UserEntity user = em.find(UserEntity.class, username);
            assertNotNull(user, "Seeded user should be findable via JPA EntityManager");
            assertFalse(user.getRoles().isEmpty(), "User roles should be populated");

            // Find a seeded account through JPA
            Long accountId = result.getAccountIds().get(0);
            AccountEntity account = em.find(AccountEntity.class, accountId);
            assertNotNull(account, "Seeded account should be findable via JPA EntityManager");
            assertNotNull(account.getUser(), "Account user relationship should be mapped");

            // Query ledger entries via JPQL
            List<LedgerEntry> entries = em.createQuery(
                    "SELECT l FROM LedgerEntry l WHERE l.account.id = :accId", LedgerEntry.class)
                    .setParameter("accId", accountId)
                    .getResultList();
            assertEquals(2, entries.size(), "Should find 2 ledger entries for the account");

            // Query audit logs via JPQL
            List<AuditLogEntity> logs = em.createQuery(
                    "SELECT a FROM AuditLogEntity a", AuditLogEntity.class)
                    .getResultList();
            assertEquals(5, logs.size(), "Should find 5 audit logs");
        } finally {
            em.close();
        }
    }
}
