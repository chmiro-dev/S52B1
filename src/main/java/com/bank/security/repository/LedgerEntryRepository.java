package com.bank.security.repository;

import com.bank.security.domain.LedgerEntry;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;

/**
 * Repository for managing LedgerEntry persistence operations.
 */
@ApplicationScoped
public class LedgerEntryRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public LedgerEntryRepository() {
    }

    public LedgerEntryRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Persists a ledger entry within the current active transaction.
     */
    @Transactional
    public LedgerEntry save(LedgerEntry entry) {
        if (entry.getId() == null) {
            entityManager.persist(entry);
            return entry;
        } else {
            return entityManager.merge(entry);
        }
    }

    /**
     * Retrieves all ledger entries associated with a specific transaction ID.
     */
    public List<LedgerEntry> findByTransactionId(String transactionId) {
        return entityManager.createQuery(
                "SELECT l FROM LedgerEntry l WHERE l.transactionId = :transactionId ORDER BY l.createdAt ASC",
                LedgerEntry.class)
                .setParameter("transactionId", transactionId)
                .getResultList();
    }

    /**
     * Retrieves all ledger entries for a specific account ID ordered
     * chronologically.
     */
    public List<LedgerEntry> findByAccountId(Long accountId) {
        return entityManager.createQuery(
                "SELECT l FROM LedgerEntry l WHERE l.accountId = :accountId ORDER BY l.createdAt DESC",
                LedgerEntry.class)
                .setParameter("accountId", accountId)
                .getResultList();
    }
}