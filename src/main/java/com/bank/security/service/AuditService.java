package com.bank.security.service;

import com.bank.security.domain.AuditLogEntity;
import com.bank.security.repository.AuditLogRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;

@ApplicationScoped
public class AuditService {

    @Inject
    private AuditLogRepository auditLogRepository;

    public AuditService() {
        // Default constructor for CDI proxying
    }

    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }

    @Transactional(Transactional.TxType.REQUIRES_NEW)
    public void recordAudit(String principal, String action, String entityName,
            String entityId, String ipAddress, String status, String payloadDelta) {
        AuditLogEntity log = new AuditLogEntity(
                principal, action, entityName, entityId, ipAddress, status, payloadDelta);
        auditLogRepository.save(log);
    }

    public List<AuditLogEntity> getLogsForPrincipal(String principal) {
        return auditLogRepository.findByPrincipal(principal);
    }

    public List<AuditLogEntity> getAllLogs() {
        return auditLogRepository.findAll();
    }
}