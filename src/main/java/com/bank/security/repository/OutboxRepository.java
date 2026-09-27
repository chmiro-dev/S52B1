package com.bank.security.repository;

import com.bank.security.domain.OutboxMessage;
import com.bank.security.domain.OutboxStatus;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

/**
 * Repository for managing OutboxMessage persistence operations.
 */
@ApplicationScoped
public class OutboxRepository {

    @PersistenceContext
    private EntityManager entityManager;

    public OutboxRepository() {
    }

    public OutboxRepository(EntityManager entityManager) {
        this.entityManager = entityManager;
    }

    /**
     * Persists a new outbox message within the active transaction.
     */
    @Transactional
    public OutboxMessage save(OutboxMessage message) {
        if (message.getId() == null) {
            entityManager.persist(message);
            return message;
        } else {
            return entityManager.merge(message);
        }
    }

    /**
     * Finds an outbox message by its primary key.
     */
    public Optional<OutboxMessage> findById(Long id) {
        return Optional.ofNullable(entityManager.find(OutboxMessage.class, id));
    }

    /**
     * Retrieves pending outbox messages ordered by creation time for processing.
     *
     * @param limit Maximum number of records to retrieve in a single batch.
     * @return List of pending OutboxMessage instances.
     */
    public List<OutboxMessage> findPendingMessages(int limit) {
        return entityManager.createQuery(
                "SELECT o FROM OutboxMessage o WHERE o.status = :status ORDER BY o.createdAt ASC",
                OutboxMessage.class)
                .setParameter("status", OutboxStatus.PENDING)
                .setMaxResults(limit)
                .getResultList();
    }

    /**
     * Updates the processing status and timestamp of an outbox message.
     */
    @Transactional
    public void updateStatus(Long id, OutboxStatus status) {
        OutboxMessage message = entityManager.find(OutboxMessage.class, id);
        if (message != null) {
            message.setStatus(status);
            if (status == OutboxStatus.PROCESSED || status == OutboxStatus.FAILED) {
                message.setProcessedAt(OffsetDateTime.now(ZoneOffset.UTC));
            }
            entityManager.merge(message);
        }
    }
}