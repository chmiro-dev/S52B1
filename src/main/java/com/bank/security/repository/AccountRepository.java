package com.bank.security.repository;

import com.bank.security.domain.AccountEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing AccountEntity persistence operations.
 */
@ApplicationScoped
public class AccountRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public AccountRepository() {
    }

    public AccountRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Persists or updates an account entity.
     */
    @Transactional
    public AccountEntity save(AccountEntity account) {
        if (account.getId() == null) {
            entityManager.persist(account);
            return account;
        } else {
            return entityManager.merge(account);
        }
    }

    /**
     * Finds an account by its primary key ID.
     */
    public Optional<AccountEntity> findById(Long id) {
        return Optional.ofNullable(entityManager.find(AccountEntity.class, id));
    }

    /**
     * Finds an account by its unique account number.
     */
    public Optional<AccountEntity> findByAccountNumber(String accountNumber) {
        List<AccountEntity> results = entityManager.createQuery(
                "SELECT a FROM AccountEntity a WHERE a.accountNumber = :accountNumber",
                AccountEntity.class)
                .setParameter("accountNumber", accountNumber)
                .getResultList();

        return results.stream().findFirst();
    }

    /**
     * Finds an account by its unique account number and applies a pessimistic write
     * lock
     * to prevent concurrent balance modifications during transactional updates.
     */
    public Optional<AccountEntity> findByAccountNumberWithLock(String accountNumber) {
        List<AccountEntity> results = entityManager.createQuery(
                "SELECT a FROM AccountEntity a WHERE a.accountNumber = :accountNumber",
                AccountEntity.class)
                .setParameter("accountNumber", accountNumber)
                .setLockMode(LockModeType.PESSIMISTIC_WRITE)
                .getResultList();

        return results.stream().findFirst();
    }

    /**
     * Retrieves all accounts associated with a specific user ID.
     */
    public List<AccountEntity> findByUserId(Long userId) {
        return entityManager.createQuery(
                "SELECT a FROM AccountEntity a WHERE a.userId = :userId ORDER BY a.createdAt DESC",
                AccountEntity.class)
                .setParameter("userId", userId)
                .getResultList();
    }

    public void deleteAll() {
        entityManager.getTransaction().begin();
        entityManager.createQuery("DELETE FROM Account a").executeUpdate();
        entityManager.getTransaction().commit();
    }
}