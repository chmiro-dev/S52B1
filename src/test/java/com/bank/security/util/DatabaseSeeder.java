package com.bank.security.util;

import com.bank.security.domain.AccountStatus;
import com.bank.security.domain.AccountType;
import com.bank.security.domain.EntryType;
import net.datafaker.Faker;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import javax.sql.DataSource;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.security.spec.InvalidKeySpecException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * DatabaseSeeder generates deterministic or pseudo-random test datasets using
 * DataFaker
 * and persists them via high-performance JDBC PreparedStatement batch
 * operations.
 * Also manages schema initialization and table truncation to maintain strict
 * test isolation.
 */
public class DatabaseSeeder {

    public static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
    public static final int PBKDF2_ITERATIONS = 210000;
    public static final int PBKDF2_KEY_LENGTH = 256;
    public static final int SALT_LENGTH_BYTES = 16;
    public static final String DEFAULT_PASSWORD = "Password123!";

    private final DataSource dataSource;
    private final Connection existingConnection;
    private final Faker faker;
    private final SecureRandom secureRandom;

    public DatabaseSeeder(DataSource dataSource) {
        this(dataSource, new Faker(Locale.US));
    }

    public DatabaseSeeder(DataSource dataSource, Faker faker) {
        this.dataSource = dataSource;
        this.existingConnection = null;
        this.faker = faker != null ? faker : new Faker(Locale.US);
        this.secureRandom = new SecureRandom();
    }

    public DatabaseSeeder(Connection connection) {
        this(connection, new Faker(Locale.US));
    }

    public DatabaseSeeder(Connection connection, Faker faker) {
        this.dataSource = null;
        this.existingConnection = connection;
        this.faker = faker != null ? faker : new Faker(Locale.US);
        this.secureRandom = new SecureRandom();
    }

    private Connection obtainConnection() throws SQLException {
        if (existingConnection != null) {
            return existingConnection;
        }
        if (dataSource != null) {
            return dataSource.getConnection();
        }
        throw new IllegalStateException("Neither DataSource nor Connection is configured.");
    }

    private void releaseConnection(Connection conn) throws SQLException {
        if (dataSource != null && conn != null && conn != existingConnection) {
            conn.close();
        }
    }

    /**
     * Executes the DDL statements from classpath resource /schema.sql to set up the
     * H2 database schema.
     */
    public void initializeSchema() throws SQLException {
        Connection conn = obtainConnection();
        boolean previousAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            try (InputStream is = getClass().getResourceAsStream("/schema.sql")) {
                if (is == null) {
                    throw new IllegalStateException("Resource /schema.sql not found on classpath.");
                }
                String fullSql = new BufferedReader(new InputStreamReader(is, StandardCharsets.UTF_8))
                        .lines()
                        .collect(Collectors.joining("\n"));

                // Split SQL by semicolon, ignoring empty lines and comments
                String[] statements = fullSql.split(";");
                try (Statement stmt = conn.createStatement()) {
                    for (String sql : statements) {
                        String trimmed = sql.trim();
                        if (!trimmed.isEmpty()) {
                            stmt.execute(trimmed);
                        }
                    }
                }
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw new SQLException("Failed to initialize schema from /schema.sql", e);
        } finally {
            conn.setAutoCommit(previousAutoCommit);
            releaseConnection(conn);
        }
    }

    /**
     * Truncates all domain tables and resets identity columns while disabling
     * foreign key checks
     * to preserve test isolation.
     */
    public void truncateAll() throws SQLException {
        Connection conn = obtainConnection();
        boolean previousAutoCommit = conn.getAutoCommit();
        try {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // Disable referential integrity for clean truncation
                stmt.execute("SET REFERENTIAL_INTEGRITY FALSE");

                String[] identityTables = { "audit_logs", "ledger_entries", "accounts" };
                for (String table : identityTables) {
                    try {
                        stmt.execute("TRUNCATE TABLE " + table + " RESTART IDENTITY");
                    } catch (SQLException e) {
                        stmt.execute("DELETE FROM " + table);
                    }
                }

                String[] regularTables = { "user_roles", "users" };
                for (String table : regularTables) {
                    try {
                        stmt.execute("TRUNCATE TABLE " + table);
                    } catch (SQLException e) {
                        stmt.execute("DELETE FROM " + table);
                    }
                }

                stmt.execute("SET REFERENTIAL_INTEGRITY TRUE");
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw new SQLException("Failed to truncate database tables", e);
        } finally {
            conn.setAutoCommit(previousAutoCommit);
            releaseConnection(conn);
        }
    }

    /**
     * Derives a 256-bit PBKDF2 key hash using PBKDF2WithHmacSHA256 and 210,000
     * iterations,
     * matching the key derivation setup used by DatabaseLoginModule.
     *
     * @param password Raw plaintext password characters.
     * @param salt     Cryptographic salt bytes (16 bytes).
     * @return 32-byte (256-bit) derived key hash.
     */
    public static byte[] hashPassword(char[] password, byte[] salt) {
        try {
            PBEKeySpec spec = new PBEKeySpec(password, salt, PBKDF2_ITERATIONS, PBKDF2_KEY_LENGTH);
            SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
            return factory.generateSecret(spec).getEncoded();
        } catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
            throw new IllegalStateException("Failed to hash password with PBKDF2", e);
        }
    }

    /**
     * Derives a 256-bit PBKDF2 key hash for a plaintext String password.
     *
     * @param password Raw plaintext password string.
     * @param salt     Cryptographic salt bytes (16 bytes).
     * @return 32-byte (256-bit) derived key hash.
     */
    public static byte[] hashPassword(String password, byte[] salt) {
        return hashPassword(password != null ? password.toCharArray() : new char[0], salt);
    }

    /**
     * Generates a secure random 16-byte salt.
     *
     * @return 16-byte random salt.
     */
    public static byte[] generateSalt() {
        byte[] salt = new byte[SALT_LENGTH_BYTES];
        new SecureRandom().nextBytes(salt);
        return salt;
    }

    /**
     * Seeds user records and their assigned roles in batch using the default test
     * password.
     *
     * @param count Number of users to seed.
     * @return List of generated usernames.
     */
    public List<String> seedUsers(int count) throws SQLException {
        return seedUsers(count, DEFAULT_PASSWORD);
    }

    /**
     * Seeds user records and their assigned roles in batch with PBKDF2 password
     * hashes matching
     * the system's key derivation setup.
     *
     * @param count       Number of users to seed.
     * @param rawPassword Plaintext password to hash with PBKDF2 for each user.
     * @return List of generated usernames.
     */
    public List<String> seedUsers(int count, String rawPassword) throws SQLException {
        if (count <= 0) {
            return Collections.emptyList();
        }

        String insertUserSql = "INSERT INTO users (username, password_hash, salt, email, first_name, last_name, enabled, created_at, updated_at) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";
        String insertRoleSql = "INSERT INTO user_roles (username, role_name) VALUES (?, ?)";

        List<String> usernames = new ArrayList<>(count);
        Connection conn = obtainConnection();
        boolean previousAutoCommit = conn.getAutoCommit();

        char[] passwordChars = (rawPassword != null ? rawPassword : DEFAULT_PASSWORD).toCharArray();

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement userStmt = conn.prepareStatement(insertUserSql);
                    PreparedStatement roleStmt = conn.prepareStatement(insertRoleSql)) {

                Instant now = Instant.now();
                for (int i = 1; i <= count; i++) {
                    String baseUsername = faker.internet().username().replaceAll("[^a-zA-Z0-9_]", "");
                    if (baseUsername.length() > 35) {
                        baseUsername = baseUsername.substring(0, 35);
                    }
                    String username = baseUsername + "_" + i + "_" + System.nanoTime() % 10000;
                    if (username.length() > 50) {
                        username = username.substring(0, 50);
                    }
                    usernames.add(username);

                    byte[] salt = new byte[SALT_LENGTH_BYTES];
                    secureRandom.nextBytes(salt);
                    byte[] hash = hashPassword(passwordChars, salt);

                    String email = "user" + i + "_" + faker.internet().emailAddress();
                    if (email.length() > 100) {
                        email = email.substring(0, 100);
                    }
                    String firstName = faker.name().firstName();
                    if (firstName.length() > 50)
                        firstName = firstName.substring(0, 50);
                    String lastName = faker.name().lastName();
                    if (lastName.length() > 50)
                        lastName = lastName.substring(0, 50);

                    userStmt.setString(1, username);
                    userStmt.setBytes(2, hash);
                    userStmt.setBytes(3, salt);
                    userStmt.setString(4, email);
                    userStmt.setString(5, firstName);
                    userStmt.setString(6, lastName);
                    userStmt.setBoolean(7, true);
                    userStmt.setTimestamp(8, Timestamp.from(now));
                    userStmt.setTimestamp(9, Timestamp.from(now));
                    userStmt.addBatch();

                    // Always add ROLE_USER
                    roleStmt.setString(1, username);
                    roleStmt.setString(2, "ROLE_USER");
                    roleStmt.addBatch();

                    // Add ROLE_ADMIN to every 5th user
                    if (i % 5 == 0) {
                        roleStmt.setString(1, username);
                        roleStmt.setString(2, "ROLE_ADMIN");
                        roleStmt.addBatch();
                    }
                }

                userStmt.executeBatch();
                roleStmt.executeBatch();
            }
            conn.commit();
            return usernames;
        } catch (Exception e) {
            conn.rollback();
            throw new SQLException("Failed to seed users in batch", e);
        } finally {
            conn.setAutoCommit(previousAutoCommit);
            releaseConnection(conn);
        }
    }

    /**
     * Seeds account records associated with the given usernames.
     *
     * @param usernames       List of existing usernames to associate accounts with.
     * @param accountsPerUser Number of accounts to generate per user.
     * @return List of generated account IDs.
     */
    public List<Long> seedAccounts(List<String> usernames, int accountsPerUser) throws SQLException {
        if (usernames == null || usernames.isEmpty() || accountsPerUser <= 0) {
            return Collections.emptyList();
        }

        String insertAccountSql = "INSERT INTO accounts (account_number, username, account_type, balance, currency, status, version, created_at, updated_at) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)";

        List<Long> accountIds = new ArrayList<>();
        Connection conn = obtainConnection();
        boolean previousAutoCommit = conn.getAutoCommit();
        AccountType[] accountTypes = AccountType.values();
        Random rnd = new Random();

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(insertAccountSql, Statement.RETURN_GENERATED_KEYS)) {
                Instant now = Instant.now();
                int accountCounter = 1;

                for (String username : usernames) {
                    for (int a = 0; a < accountsPerUser; a++) {
                        String accountNumber = String.format("ACCT-%06d-%04d", accountCounter++, rnd.nextInt(10000));
                        AccountType accountType = accountTypes[a % accountTypes.length];
                        BigDecimal balance = BigDecimal.valueOf(100.0 + rnd.nextDouble() * 25000.0).setScale(4,
                                RoundingMode.HALF_UP);

                        pstmt.setString(1, accountNumber);
                        pstmt.setString(2, username);
                        pstmt.setString(3, accountType.name());
                        pstmt.setBigDecimal(4, balance);
                        pstmt.setString(5, "USD");
                        pstmt.setString(6, AccountStatus.ACTIVE.name());
                        pstmt.setLong(7, 0L);
                        pstmt.setTimestamp(8, Timestamp.from(now));
                        pstmt.setTimestamp(9, Timestamp.from(now));
                        pstmt.addBatch();
                    }
                }

                pstmt.executeBatch();

                try (ResultSet generatedKeys = pstmt.getGeneratedKeys()) {
                    while (generatedKeys.next()) {
                        accountIds.add(generatedKeys.getLong(1));
                    }
                }
            }
            conn.commit();
            return accountIds;
        } catch (Exception e) {
            conn.rollback();
            throw new SQLException("Failed to seed accounts in batch", e);
        } finally {
            conn.setAutoCommit(previousAutoCommit);
            releaseConnection(conn);
        }
    }

    /**
     * Seeds ledger entries tied to the specified account IDs.
     *
     * @param accountIds        List of existing account IDs.
     * @param entriesPerAccount Number of ledger entries per account.
     */
    public void seedLedgerEntries(List<Long> accountIds, int entriesPerAccount) throws SQLException {
        if (accountIds == null || accountIds.isEmpty() || entriesPerAccount <= 0) {
            return;
        }

        String insertLedgerSql = "INSERT INTO ledger_entries (transaction_id, account_id, entry_type, amount, currency, balance_after, description, created_at) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        Connection conn = obtainConnection();
        boolean previousAutoCommit = conn.getAutoCommit();
        EntryType[] entryTypes = EntryType.values();
        Random rnd = new Random();

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(insertLedgerSql)) {
                Instant now = Instant.now();

                for (Long accountId : accountIds) {
                    BigDecimal runningBalance = new BigDecimal("1000.0000");

                    for (int e = 0; e < entriesPerAccount; e++) {
                        String txId = "TX-" + UUID.randomUUID().toString();
                        EntryType entryType = entryTypes[e % entryTypes.length];
                        BigDecimal amount = BigDecimal.valueOf(10.0 + rnd.nextDouble() * 500.0).setScale(4,
                                RoundingMode.HALF_UP);

                        if (entryType == EntryType.CREDIT) {
                            runningBalance = runningBalance.add(amount);
                        } else {
                            runningBalance = runningBalance.subtract(amount);
                        }

                        String description = (entryType == EntryType.CREDIT ? "Deposit: " : "Withdrawal: ")
                                + faker.commerce().productName();
                        if (description.length() > 255) {
                            description = description.substring(0, 255);
                        }

                        pstmt.setString(1, txId);
                        pstmt.setLong(2, accountId);
                        pstmt.setString(3, entryType.name());
                        pstmt.setBigDecimal(4, amount);
                        pstmt.setString(5, "USD");
                        pstmt.setBigDecimal(6, runningBalance);
                        pstmt.setString(7, description);
                        pstmt.setTimestamp(8, Timestamp.from(now.minusSeconds((entriesPerAccount - e) * 60L)));
                        pstmt.addBatch();
                    }
                }

                pstmt.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw new SQLException("Failed to seed ledger entries in batch", e);
        } finally {
            conn.setAutoCommit(previousAutoCommit);
            releaseConnection(conn);
        }
    }

    /**
     * Seeds audit log records.
     *
     * @param count Number of audit log entries to seed.
     */
    public void seedAuditLogs(int count) throws SQLException {
        if (count <= 0) {
            return;
        }

        String insertAuditSql = "INSERT INTO audit_logs (principal, action, entity_name, entity_id, ip_address, payload_delta, status, timestamp) "
                +
                "VALUES (?, ?, ?, ?, ?, ?, ?, ?)";

        String[] actions = { "USER_LOGIN", "TRANSFER_FUNDS", "UPDATE_PASSWORD", "FREEZE_ACCOUNT", "CREATE_ACCOUNT" };
        String[] entities = { "UserEntity", "AccountEntity", "LedgerEntry" };
        String[] statuses = { "SUCCESS", "FAILED" };

        Connection conn = obtainConnection();
        boolean previousAutoCommit = conn.getAutoCommit();
        // Random rnd = new Random();

        try {
            conn.setAutoCommit(false);
            try (PreparedStatement pstmt = conn.prepareStatement(insertAuditSql)) {
                Instant now = Instant.now();

                for (int i = 0; i < count; i++) {
                    String principal = "user_" + faker.internet().username().replaceAll("[^a-zA-Z0-9_]", "");
                    if (principal.length() > 100)
                        principal = principal.substring(0, 100);

                    String action = actions[i % actions.length];
                    String entityName = entities[i % entities.length];
                    String entityId = "ID-" + (1000 + i);
                    String ipAddress = faker.internet().ipV4Address();
                    String status = statuses[i % statuses.length];
                    String payloadDelta = String.format("{\"action\":\"%s\",\"entityId\":\"%s\",\"seq\":%d}", action,
                            entityId, i);

                    pstmt.setString(1, principal);
                    pstmt.setString(2, action);
                    pstmt.setString(3, entityName);
                    pstmt.setString(4, entityId);
                    pstmt.setString(5, ipAddress);
                    pstmt.setString(6, payloadDelta);
                    pstmt.setString(7, status);
                    pstmt.setTimestamp(8, Timestamp.from(now.minusSeconds(i * 30L)));
                    pstmt.addBatch();
                }

                pstmt.executeBatch();
            }
            conn.commit();
        } catch (Exception e) {
            conn.rollback();
            throw new SQLException("Failed to seed audit logs in batch", e);
        } finally {
            conn.setAutoCommit(previousAutoCommit);
            releaseConnection(conn);
        }
    }

    /**
     * Complete seed execution: seeds users, accounts, ledger entries, and audit
     * logs.
     *
     * @param userCount               Number of users to create.
     * @param accountsPerUser         Number of accounts per user.
     * @param ledgerEntriesPerAccount Number of ledger entries per account.
     * @param auditLogCount           Number of audit logs to generate.
     * @return SeederResult summarizing generated records.
     */
    public SeederResult seedAll(int userCount, int accountsPerUser, int ledgerEntriesPerAccount, int auditLogCount)
            throws SQLException {
        List<String> usernames = seedUsers(userCount);
        List<Long> accountIds = seedAccounts(usernames, accountsPerUser);
        seedLedgerEntries(accountIds, ledgerEntriesPerAccount);
        seedAuditLogs(auditLogCount);

        return new SeederResult(usernames, accountIds, accountIds.size() * ledgerEntriesPerAccount, auditLogCount);
    }

    /**
     * Default seedAll method seeding a balanced test dataset.
     */
    public SeederResult seedAll() throws SQLException {
        return seedAll(10, 2, 5, 20);
    }

    /**
     * Value container for seeded test dataset identifiers and counts.
     */
    public static class SeederResult {
        private final List<String> usernames;
        private final List<Long> accountIds;
        private final int ledgerEntryCount;
        private final int auditLogCount;

        public SeederResult(List<String> usernames, List<Long> accountIds, int ledgerEntryCount, int auditLogCount) {
            this.usernames = usernames != null ? Collections.unmodifiableList(usernames) : Collections.emptyList();
            this.accountIds = accountIds != null ? Collections.unmodifiableList(accountIds) : Collections.emptyList();
            this.ledgerEntryCount = ledgerEntryCount;
            this.auditLogCount = auditLogCount;
        }

        public List<String> getUsernames() {
            return usernames;
        }

        public List<Long> getAccountIds() {
            return accountIds;
        }

        public int getUserCount() {
            return usernames.size();
        }

        public int getAccountCount() {
            return accountIds.size();
        }

        public int getLedgerEntryCount() {
            return ledgerEntryCount;
        }

        public int getAuditLogCount() {
            return auditLogCount;
        }
    }
}
