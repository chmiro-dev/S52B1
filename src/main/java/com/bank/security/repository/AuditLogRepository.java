package com.bank.security.repository;

import com.bank.security.domain.AuditLogEntity;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;

@ApplicationScoped
public class AuditLogRepository {

    @Inject
    @PersistenceContext
    private EntityManager entityManager;

    public void save(AuditLogEntity log) {
        entityManager.persist(log);
    }

    public List<AuditLogEntity> findByPrincipal(String principal) {
        return entityManager.createQuery(
                "SELECT a FROM AuditLogEntity a WHERE a.principal = :principal ORDER BY a.timestamp DESC",
                AuditLogEntity.class)
                .setParameter("principal", principal)
                .getResultList();
    }

    public List<AuditLogEntity> findAll() {
        return entityManager.createQuery(
                "SELECT a FROM AuditLogEntity a ORDER BY a.timestamp DESC",
                AuditLogEntity.class)
                .getResultList();
    }

    public void setEntityManager(EntityManager entityManager) {
        this.entityManager = entityManager;
    }
}