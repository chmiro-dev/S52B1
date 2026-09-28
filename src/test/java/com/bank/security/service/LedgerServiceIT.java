package com.bank.security.service;

import com.bank.security.domain.AccountEntity;
import com.bank.security.domain.AccountStatus;
import com.bank.security.domain.AccountType;
import com.bank.security.domain.UserEntity;
import com.bank.security.domain.EntryType;
import com.bank.security.domain.LedgerEntry;
import com.bank.security.domain.OutboxMessage;
import com.bank.security.repository.AccountRepository;
import com.bank.security.repository.LedgerEntryRepository;
import com.bank.security.repository.OutboxRepository;

import jakarta.persistence.Persistence;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.security.SecureRandom;
import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@DisplayName("LedgerService Integration Test Suite")
public class LedgerServiceIT {

    private static EntityManagerFactory emf;
    private EntityManager em;
    private AccountRepository accountRepository;
    private LedgerEntryRepository ledgerEntryRepository;
    private OutboxRepository outboxRepository;
    private LedgerService ledgerService;

    private static final String SOURCE_ACCOUNT = "ACC-SOURCE-1001";
    private static final String DEST_ACCOUNT = "ACC-DEST-1002";

    @BeforeAll
    static void initEmf() {
        emf = Persistence.createEntityManagerFactory("bankTestPU");
    }

    @AfterAll
    static void closeEmf() {
        if (emf != null && emf.isOpen()) {
            emf.close();
        }
    }

    @BeforeEach
    void setUp() {
        // Delete the database and recreate it before each test to ensure a clean state

        // Instantiate repositories and service
        em = emf.createEntityManager();

        accountRepository = new AccountRepository(em);
        ledgerEntryRepository = new LedgerEntryRepository(em);
        outboxRepository = new OutboxRepository(em);
        ledgerService = new LedgerService(accountRepository, ledgerEntryRepository, outboxRepository);

        em.getTransaction().begin();
        em.createQuery("DELETE FROM LedgerEntry").executeUpdate();
        em.createQuery("DELETE FROM OutboxMessage").executeUpdate();
        em.createQuery("DELETE FROM AccountEntity").executeUpdate();
        em.createQuery("DELETE FROM UserEntity").executeUpdate();
        em.getTransaction().commit();

        em.clear();

        // Seed initial active accounts for testing
        /*
         * (String accountNumber, UserEntity user, AccountType accountType, BigDecimal
         * balance, String currency)
         */
        // create new UserEntity for source and destination accounts if needed
        // add user entity to sourceAccount and destAccount if needed
        em.getTransaction().begin();

        UserEntity user1 = new UserEntity();
        user1.setUsername("test1");
        SecureRandom random = new SecureRandom();
        byte[] bytes16 = new byte[2];
        random.nextBytes(bytes16);
        user1.setSalt(bytes16);
        byte[] bytes32 = new byte[4];
        random.nextBytes(bytes32);
        user1.setPasswordHash(bytes32);

        em.persist(user1);

        AccountEntity sourceAccount = new AccountEntity();
        sourceAccount.setUser(user1);
        sourceAccount.setAccountNumber(SOURCE_ACCOUNT);
        sourceAccount.setAccountType(AccountType.CHECKING);
        sourceAccount.setBalance(new BigDecimal("1000.00"));
        sourceAccount.setStatus(AccountStatus.ACTIVE);

        UserEntity user2 = new UserEntity();
        user2.setUsername("test2");
        bytes16 = new byte[2];
        random.nextBytes(bytes16);
        user2.setSalt(bytes16);
        bytes32 = new byte[4];
        random.nextBytes(bytes32);
        user2.setPasswordHash(bytes32);

        em.persist(user2);

        AccountEntity destAccount = new AccountEntity();
        destAccount.setUser(user2);
        destAccount.setAccountNumber(DEST_ACCOUNT);
        destAccount.setAccountType(AccountType.SAVINGS);
        destAccount.setBalance(new BigDecimal("250.00"));
        destAccount.setStatus(AccountStatus.ACTIVE);

        accountRepository.save(sourceAccount);
        accountRepository.save(destAccount);
        em.getTransaction().commit();
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            try {
                EntityTransaction tx = em.getTransaction();
                if (!tx.isActive()) {
                    tx.begin();
                }
                accountRepository.deleteAll();
                tx.commit();
            } catch (Exception e) {
                if (em.getTransaction().isActive()) {
                    em.getTransaction().rollback();
                }
            } finally {
                em.close();
            }
        }
    }

    @Test
    @DisplayName("Should execute successful transfer, update balances, and persist outbox event")
    void testSuccessfulTransfer() {
        BigDecimal transferAmount = new BigDecimal("200.00");
        String description = "Monthly savings transfer";

        // Execute transfer via LedgerService
        em.getTransaction().begin();
        String transactionId = ledgerService.transferFunds(SOURCE_ACCOUNT, DEST_ACCOUNT, transferAmount, description);
        em.getTransaction().commit();

        assertNotNull(transactionId, "Returned transaction ID should not be null");

        // 1. Verify updated Account balances
        AccountEntity updatedSource = accountRepository.findByAccountNumber(SOURCE_ACCOUNT).orElseThrow();
        AccountEntity updatedDest = accountRepository.findByAccountNumber(DEST_ACCOUNT).orElseThrow();

        assertEquals(0, new BigDecimal("800.00").compareTo(updatedSource.getBalance()),
                "Source account balance should decrease by transfer amount");
        assertEquals(0, new BigDecimal("450.00").compareTo(updatedDest.getBalance()),
                "Destination account balance should increase by transfer amount");

        // 2. Verify paired LedgerEntry records (Double-Entry Invariant)
        List<LedgerEntry> entries = ledgerEntryRepository.findByTransactionId(transactionId);
        assertEquals(2, entries.size(), "Exactly two double-entry records must be persisted");

        LedgerEntry debit = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .findFirst()
                .orElseThrow();
        LedgerEntry credit = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .findFirst()
                .orElseThrow();

        assertEquals(0, transferAmount.compareTo(debit.getAmount()));
        assertEquals(0, transferAmount.compareTo(credit.getAmount()));
        assertEquals(SOURCE_ACCOUNT, debit.getAccount().getAccountNumber());
        assertEquals(DEST_ACCOUNT, credit.getAccount().getAccountNumber());

        // 3. Verify OutboxMessage generation
        List<OutboxMessage> messages = outboxRepository.findByAggregateId(transactionId);
        assertEquals(1, messages.size(), "Exactly one OutboxMessage should be created for the transaction");

        OutboxMessage outboxMessage = messages.get(0);
        assertEquals("LEDGER_TRANSACTION", outboxMessage.getAggregateType());
        assertEquals("FUNDS_TRANSFERRED", outboxMessage.getEventType());
        assertTrue(outboxMessage.getPayload().contains(transactionId), "Payload must contain transaction ID");
        assertTrue(outboxMessage.getPayload().contains(SOURCE_ACCOUNT), "Payload must contain source account number");
    }

    @Test
    @DisplayName("Should throw IllegalStateException when source account has insufficient funds")
    void testTransferFailsDueToInsufficientFunds() {
        BigDecimal excessiveAmount = new BigDecimal("10000.00");

        // Begin transaction so pessimistic lock query can execute
        em.getTransaction().begin();
        try {
            IllegalStateException exception = assertThrows(
                    IllegalStateException.class,
                    () -> ledgerService.transferFunds(SOURCE_ACCOUNT, DEST_ACCOUNT, excessiveAmount,
                            "Overdraft attempt"));

            // Optional: Verify the exact error message thrown by your service
            assertEquals("Insufficient funds in account: ACC-SOURCE-1001", exception.getMessage());
        } finally {
            // Roll back any uncommitted state changes
            if (em.getTransaction().isActive()) {
                em.getTransaction().rollback();
            }
        }
    }

    @Test
    @DisplayName("Should throw IllegalArgumentException when transfer amount is negative or zero")
    void testTransferFailsForInvalidAmount() {
        BigDecimal invalidAmount = new BigDecimal("-50.00");
        EntityTransaction t = em.getTransaction();
        t.begin();
        assertThrows(
                IllegalArgumentException.class,
                () -> ledgerService.transferFunds(SOURCE_ACCOUNT, DEST_ACCOUNT, invalidAmount, "Invalid amount"));
        t.commit();
    }

}