package com.bank.security.service;

import com.bank.security.domain.AuditLogEntity;
import com.bank.security.interceptor.AuditInterceptor;
import com.bank.security.repository.AuditLogRepository;
import com.bank.security.service.AuditService;

import jakarta.enterprise.inject.Default;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityTransaction;
import jakarta.inject.Inject;

import org.jboss.weld.environment.se.Weld;
import org.jboss.weld.environment.se.WeldContainer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class AuditServiceIT {

    private static Weld weldContainer;
    private static WeldContainer container;
    @Inject
    private AuditService auditService;
    private AuditedTestBean auditedTestBean;
    private EntityManager entityManager;
    private AuditLogRepository auditLogRepository;

    @BeforeAll
    public static void startContainer() {
        weldContainer = new Weld();
        weldContainer.disableDiscovery();
        weldContainer.addInterceptor(AuditInterceptor.class); // Explicitly enable interceptor
        weldContainer.addBeanClasses(
                AuditTestProducer.class,
                AuditLogRepository.class,
                AuditService.class,
                AuditInterceptor.class,
                AuditedTestBean.class);
        container = weldContainer.initialize();
    }

    @AfterAll
    public static void stopContainer() {
        if (container != null) {
            container.shutdown();
        }
    }

    @BeforeEach
    public void setUp() {
        auditService = container.select(AuditService.class).get();
        auditedTestBean = container.select(AuditedTestBean.class).get();
        entityManager = container.select(EntityManager.class).get();
        auditLogRepository = container.select(AuditLogRepository.class).get();

        auditLogRepository.setEntityManager(entityManager);

        EntityTransaction tx = entityManager.getTransaction();
        if (!tx.isActive()) {
            tx.begin();
        }
    }

    @AfterEach
    public void tearDown() {
        if (entityManager != null && entityManager.getTransaction().isActive()) {
            entityManager.getTransaction().commit();
        }
    }

    @Test
    @DisplayName("Should persist and retrieve audit logs for a given principal")
    public void testRecordAndRetrieveAuditLog() {
        String principal = "user_test_01";
        String action = "TRANSFER_FUNDS";
        String entityName = "LedgerService";
        String entityId = "TXN-99812";
        String ipAddress = "192.168.1.50";
        String status = "SUCCESS";
        String payloadDelta = "{\"fromAccount\":\"ACC-101\",\"toAccount\":\"ACC-102\",\"amount\":150.00}";

        auditService.recordAudit(principal, action, entityName, entityId, ipAddress, status, payloadDelta);

        List<AuditLogEntity> logs = auditService.getLogsForPrincipal(principal);

        assertFalse(logs.isEmpty(), "Audit log list should not be empty");
        assertEquals(1, logs.size(), "Should find exactly one audit record for principal");

        AuditLogEntity log = logs.get(0);
        assertEquals(principal, log.getPrincipal());
        assertEquals(action, log.getAction());
        assertEquals(entityName, log.getEntityName());
        assertEquals(entityId, log.getEntityId());
        assertEquals(ipAddress, log.getIpAddress());
        assertEquals(status, log.getStatus());
        assertEquals(payloadDelta, log.getPayloadDelta());
        assertNotNull(log.getTimestamp(), "Timestamp should be populated automatically on persist");
    }

    @Test
    @DisplayName("Should automatically audit methods annotated with @Audited via CDI Interceptor")
    public void testAuditedInterceptorInvocation() {
        String result = auditedTestBean.executeAction("sampleData");
        assertEquals("RESULT_sampleData", result);

        if (entityManager.getTransaction().isActive()) {
            entityManager.flush();
        }

        List<AuditLogEntity> allLogs = auditService.getAllLogs();
        assertFalse(allLogs.isEmpty(), "Audit log should contain an entry from the interceptor");

        AuditLogEntity interceptedLog = allLogs.stream()
                .filter(log -> "EXECUTE_SENSITIVE_ACTION".equals(log.getAction()))
                .findFirst()
                .orElse(null);

        assertNotNull(interceptedLog, "Should find audit log entry created by @Audited interceptor");
        assertEquals("AuditedTestBean", interceptedLog.getEntityName());
        assertEquals("SUCCESS", interceptedLog.getStatus());
    }

    @Test
    @DisplayName("Should retrieve all audit logs across multiple principals")
    public void testGetAllLogs() {
        auditService.recordAudit("user_a", "LOGIN", "AuthService", null, "10.0.0.1", "SUCCESS", null);
        auditService.recordAudit("user_b", "PASSWORD_CHANGE", "UserService", "USR-44", "10.0.0.2", "SUCCESS", null);

        List<AuditLogEntity> allLogs = auditService.getAllLogs();

        assertTrue(allLogs.size() >= 2, "Should retrieve at least two recorded audit entries");
    }
}