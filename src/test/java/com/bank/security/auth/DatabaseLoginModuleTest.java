package com.bank.security.auth;

import com.bank.security.util.DatabaseSeeder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.security.auth.Subject;
import javax.security.auth.callback.Callback;
import javax.security.auth.callback.CallbackHandler;
import javax.security.auth.callback.NameCallback;
import javax.security.auth.callback.PasswordCallback;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HashMap;

import static org.junit.jupiter.api.Assertions.*;

public class DatabaseLoginModuleTest {

    private static final String DB_URL = "jdbc:h2:mem:bankdb;DB_CLOSE_DELAY=-1";

    @BeforeEach
    void setupInMemoryDatabase() throws SQLException {
        try (Connection conn = DriverManager.getConnection(DB_URL, "sa", "")) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");
                stmt.execute("""
                            CREATE TABLE IF NOT EXISTS users (
                                username VARCHAR(50) PRIMARY KEY,
                                password_hash VARBINARY NOT NULL,
                                salt VARBINARY NOT NULL,
                                email VARCHAR(100),
                                first_name VARCHAR(50),
                                last_name VARCHAR(50),
                                enabled BOOLEAN NOT NULL DEFAULT TRUE,
                                created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
                                updated_at TIMESTAMP WITH TIME ZONE
                            );
                            CREATE TABLE IF NOT EXISTS user_roles (
                                username VARCHAR(50),
                                role_name VARCHAR(50)
                            );
                        """);
                stmt.execute("DELETE FROM user_roles");
                stmt.execute("DELETE FROM users");
                stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
        }
    }

    @Test
    @DisplayName("Verify fetchUserFromDatabase correctly maps salt and password_hash columns into UserData")
    void testFetchUserFromDatabaseColumnMapping() throws SQLException {
        String username = "alice_test";
        byte[] expectedSalt = new byte[] { 0x01, 0x02, 0x03, 0x04, 0x05, 0x06, 0x07, 0x08, 0x09, 0x0A, 0x0B, 0x0C, 0x0D,
                0x0E, 0x0F, 0x10 };
        byte[] expectedHash = new byte[] {
                (byte) 0xAA, (byte) 0xBB, (byte) 0xCC, (byte) 0xDD, 0x11, 0x22, 0x33, 0x44,
                0x55, 0x66, 0x77, (byte) 0x88, (byte) 0x99, 0x00, 0x12, 0x34,
                0x56, 0x78, (byte) 0x9A, (byte) 0xBC, (byte) 0xDE, (byte) 0xF0, 0x01, 0x23,
                0x45, 0x67, (byte) 0x89, (byte) 0xAB, (byte) 0xCD, (byte) 0xEF, 0x42, 0x77
        };

        // Insert test user directly into the database
        try (Connection conn = DriverManager.getConnection(DB_URL, "sa", "")) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO users (username, password_hash, salt, email, first_name, last_name) VALUES (?, ?, ?, ?, ?, ?)")) {
                stmt.setString(1, username);
                stmt.setBytes(2, expectedHash);
                stmt.setBytes(3, expectedSalt);
                stmt.setString(4, "alice@example.com");
                stmt.setString(5, "Alice");
                stmt.setString(6, "Smith");
                stmt.executeUpdate();
            }

            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO user_roles (username, role_name) VALUES (?, ?), (?, ?)")) {
                stmt.setString(1, username);
                stmt.setString(2, "ROLE_USER");
                stmt.setString(3, username);
                stmt.setString(4, "ROLE_TELLER");
                stmt.executeUpdate();
            }
        }

        DatabaseLoginModule loginModule = new DatabaseLoginModule();
        UserData userData = loginModule.fetchUserFromDatabase(username);

        assertNotNull(userData, "UserData must not be null for existing user");
        assertNotNull(userData.salt(), "Salt must not be null");
        assertNotNull(userData.passwordHash(), "Password hash must not be null");

        // Verify exact byte mapping from database columns
        assertArrayEquals(expectedSalt, userData.salt(), "salt column must map correctly to UserData.salt()");
        assertArrayEquals(expectedHash, userData.passwordHash(),
                "password_hash column must map correctly to UserData.passwordHash()");

        // Verify roles
        assertEquals(2, userData.roles().size());
        assertTrue(userData.roles().contains("ROLE_USER"));
        assertTrue(userData.roles().contains("ROLE_TELLER"));
    }

    @Test
    @DisplayName("Verify pendingUserData in DatabaseLoginModule stores mapped salt and password_hash during login")
    void testPendingUserDataMappingDuringLogin() throws Exception {
        String username = "bob_test";
        String password = "SecretPassword!123";
        byte[] salt = DatabaseSeeder.generateSalt();
        byte[] hash = DatabaseSeeder.hashPassword(password, salt);

        // Insert bob with PBKDF2 hash and salt
        try (Connection conn = DriverManager.getConnection(DB_URL, "sa", "")) {
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO users (username, password_hash, salt) VALUES (?, ?, ?)")) {
                stmt.setString(1, username);
                stmt.setBytes(2, hash);
                stmt.setBytes(3, salt);
                stmt.executeUpdate();
            }
            try (PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO user_roles (username, role_name) VALUES (?, ?)")) {
                stmt.setString(1, username);
                stmt.setString(2, "ROLE_USER");
                stmt.executeUpdate();
            }
        }

        DatabaseLoginModule loginModule = new DatabaseLoginModule();
        Subject subject = new Subject();
        CallbackHandler handler = callbacks -> {
            for (Callback cb : callbacks) {
                if (cb instanceof NameCallback nc) {
                    nc.setName(username);
                } else if (cb instanceof PasswordCallback pc) {
                    pc.setPassword(password.toCharArray());
                }
            }
        };

        loginModule.initialize(subject, handler, new HashMap<>(), new HashMap<>());
        boolean loginSuccess = loginModule.login();
        assertTrue(loginSuccess, "Login must succeed with valid credentials");

        // Verify this.pendingUserData holds the mapped salt and password_hash before
        // commit()
        assertNotNull(loginModule.pendingUserData, "this.pendingUserData must be populated during login");
        assertArrayEquals(salt, loginModule.pendingUserData.salt(),
                "this.pendingUserData.salt() must match DB salt column");
        assertArrayEquals(hash, loginModule.pendingUserData.passwordHash(),
                "this.pendingUserData.passwordHash() must match DB password_hash column");

        // Verify commit() promotes pendingUserData to subject principals and clears
        // pendingUserData
        assertTrue(loginModule.commit(), "Commit must succeed");
        assertNull(loginModule.pendingUserData, "this.pendingUserData must be cleared after commit()");
        assertEquals(1, subject.getPrincipals(com.bank.security.auth.model.CustomPrincipal.class).size());
    }

    @Test
    @DisplayName("Verify fetchUserFromDatabase returns null for non-existent user")
    void testFetchUserFromDatabaseNonExistentUser() {
        DatabaseLoginModule loginModule = new DatabaseLoginModule();
        UserData userData = loginModule.fetchUserFromDatabase("unknown_user_999");
        assertNull(userData, "fetchUserFromDatabase must return null when username is not found in database");
    }
}