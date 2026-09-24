package com.bank.security.domain;

import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

public class EntityPersistenceTest {

    private static EntityManagerFactory emf;
    private EntityManager em;

    @BeforeAll
    static void initEmf() {
        try {
            emf = Persistence.createEntityManagerFactory("bankPU");
        } catch (Exception e) {
            e.printStackTrace();
            throw e;
        }
    }

    @AfterAll
    static void closeEmf() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @BeforeEach
    void setUp() {
        em = emf.createEntityManager();
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            em.close();
        }
    }

    @Test
    void testUserEntityPersistence() {
        em.getTransaction().begin();

        byte[] hash = new byte[]{1, 2, 3, 4};
        byte[] salt = new byte[]{5, 6, 7, 8};
        UserEntity user = new UserEntity("john_doe", hash, salt, "john@example.com", "John", "Doe");
        user.addRole("ROLE_USER");
        user.addRole("ROLE_ADMIN");

        em.persist(user);
        em.getTransaction().commit();
        em.clear();

        UserEntity found = em.find(UserEntity.class, "john_doe");
        assertNotNull(found);
        assertEquals("john@example.com", found.getEmail());
        assertEquals("John", found.getFirstName());
        assertEquals("Doe", found.getLastName());
        assertTrue(found.isEnabled());
        assertNotNull(found.getCreatedAt());
        assertEquals(2, found.getRoles().size());
        assertTrue(found.getRoles().contains("ROLE_USER"));
        assertTrue(found.getRoles().contains("ROLE_ADMIN"));
    }

    @Test
    void testAccountEntityPersistence() {
        em.getTransaction().begin();

        UserEntity user = new UserEntity("jane_banker", new byte[]{1}, new byte[]{2});
        em.persist(user);

        AccountEntity account = new AccountEntity(
                "ACCT-100200300",
                user,
                AccountType.CHECKING,
                new BigDecimal("1500.5000"),
                "USD"
        );
        em.persist(account);
        em.getTransaction().commit();
        em.clear();

        AccountEntity found = em.find(AccountEntity.class, account.getId());
        assertNotNull(found);
        assertEquals("ACCT-100200300", found.getAccountNumber());
        assertEquals(AccountType.CHECKING, found.getAccountType());
        assertEquals(AccountStatus.ACTIVE, found.getStatus());
        assertEquals(0, new BigDecimal("1500.5000").compareTo(found.getBalance()));
        assertEquals("USD", found.getCurrency());
        assertNotNull(found.getUser());
        assertEquals("jane_banker", found.getUser().getUsername());
        assertNotNull(found.getCreatedAt());
    }

    @Test
    void testLedgerEntryPersistence() {
        em.getTransaction().begin();

        UserEntity user = new UserEntity("ledger_user", new byte[]{1}, new byte[]{2});
        em.persist(user);

        AccountEntity account = new AccountEntity("ACCT-456", user, AccountType.SAVINGS, new BigDecimal("2000.0000"), "USD");
        em.persist(account);

        String txId = UUID.randomUUID().toString();
        LedgerEntry entry = new LedgerEntry(
                txId,
                account,
                EntryType.CREDIT,
                new BigDecimal("500.0000"),
                "USD",
                new BigDecimal("2500.0000"),
                "Direct deposit payroll"
        );
        em.persist(entry);
        em.getTransaction().commit();
        em.clear();

        LedgerEntry found = em.find(LedgerEntry.class, entry.getId());
        assertNotNull(found);
        assertEquals(txId, found.getTransactionId());
        assertEquals(EntryType.CREDIT, found.getEntryType());
        assertEquals(0, new BigDecimal("500.0000").compareTo(found.getAmount()));
        assertEquals("Direct deposit payroll", found.getDescription());
        assertNotNull(found.getCreatedAt());
        assertEquals("ACCT-456", found.getAccount().getAccountNumber());
    }

    @Test
    void testAuditLogEntityPersistence() {
        em.getTransaction().begin();

        AuditLogEntity log = new AuditLogEntity(
                "admin",
                "TRANSFER",
                "AccountEntity",
                "ACCT-100200300",
                "192.168.1.10",
                "{\"amount\": 500.00, \"source\": \"ACCT-100\", \"destination\": \"ACCT-200\"}",
                "SUCCESS"
        );
        em.persist(log);
        em.getTransaction().commit();
        em.clear();

        AuditLogEntity found = em.find(AuditLogEntity.class, log.getId());
        assertNotNull(found);
        assertEquals("admin", found.getPrincipal());
        assertEquals("TRANSFER", found.getAction());
        assertEquals("AccountEntity", found.getEntityName());
        assertEquals("192.168.1.10", found.getIpAddress());
        assertEquals("SUCCESS", found.getStatus());
        assertNotNull(found.getPayloadDelta());
        assertNotNull(found.getTimestamp());
    }
}
