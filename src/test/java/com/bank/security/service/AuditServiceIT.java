package com.bank.security.service;

import com.bank.security.domain.AuditLogEntity;
import com.bank.security.repository.AuditLogRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.EntityTransaction;
import jakarta.persistence.Persistence;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AuditService Integration Test Suite")
public class AuditServiceIT {

    private static EntityManagerFactory emf;
    private EntityManager em;
    private AuditLogRepository auditLogRepository;
    private AuditService auditService;

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
        em = emf.createEntityManager();
        auditLogRepository = new AuditLogRepository();
        auditLogRepository.setEntityManager(em);
        auditService = new AuditService(auditLogRepository);

        em.getTransaction().begin();
        em.createQuery("DELETE FROM AuditLogEntity").executeUpdate();
        em.getTransaction().commit();
        em.clear();
    }

    @AfterEach
    void tearDown() {
        if (em != null && em.isOpen()) {
            try {
                EntityTransaction tx = em.getTransaction();
                if (!tx.isActive()) {
                    tx.begin();
                }
                em.createQuery("DELETE FROM AuditLogEntity").executeUpdate();
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
    @DisplayName("Should successfully record and retrieve audit logs via AuditService")
    void testRecordAndRetrieveAuditLog() {
        String principal = "test_user";
        String action = "TRANSFER_FUNDS";
        String entityName = "LedgerService";
        String entityId = "TXN-12345";
        String ipAddress = "127.0.0.1";
        String status = "SUCCESS";
        String payloadDelta = "{\"amount\":100.00}";

        em.getTransaction().begin();
        auditService.recordAudit(principal, action, entityName, entityId, ipAddress, status, payloadDelta);
        em.getTransaction().commit();

        List<AuditLogEntity> logs = auditService.getLogsForPrincipal(principal);
        assertFalse(logs.isEmpty(), "Audit log list should not be empty");
        assertEquals(1, logs.size());

        AuditLogEntity savedLog = logs.get(0);
        assertEquals(principal, savedLog.getPrincipal());
        assertEquals(action, savedLog.getAction());
        assertEquals(entityName, savedLog.getEntityName());
        assertEquals(entityId, savedLog.getEntityId());
        assertEquals(status, savedLog.getStatus());
    }

    @Test
    @DisplayName("Should retrieve logs filtered by principal")
    void testGetLogsForPrincipal() {
        String principal = "alice";

        em.getTransaction().begin();
        auditService.recordAudit(principal, "LOGIN", "AuthService", null, "192.168.1.1", "SUCCESS", null);
        auditService.recordAudit("bob", "LOGIN", "AuthService", null, "192.168.1.2", "SUCCESS", null);
        em.getTransaction().commit();

        List<AuditLogEntity> logs = auditService.getLogsForPrincipal(principal);
        assertNotNull(logs);
        assertEquals(1, logs.size());
        assertEquals(principal, logs.get(0).getPrincipal());
    }
}