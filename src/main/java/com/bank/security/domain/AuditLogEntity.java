package com.bank.security.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "audit_logs")
public class AuditLogEntity implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "principal", nullable = false, length = 100)
    private String principal;

    @Column(name = "action", nullable = false, length = 100)
    private String action;

    @Column(name = "entity_name", length = 100)
    private String entityName;

    @Column(name = "entity_id", length = 100)
    private String entityId;

    @Column(name = "ip_address", length = 45)
    private String ipAddress;

    @Column(name = "payload_delta", columnDefinition = "TEXT")
    private String payloadDelta;

    @Column(name = "status", length = 20)
    private String status;

    @Column(name = "timestamp", nullable = false, updatable = false)
    private Instant timestamp;

    public AuditLogEntity() {
    }

    public AuditLogEntity(String principal, String action, String entityName, String entityId, String ipAddress, String payloadDelta, String status) {
        this.principal = principal;
        this.action = action;
        this.entityName = entityName;
        this.entityId = entityId;
        this.ipAddress = ipAddress;
        this.payloadDelta = payloadDelta;
        this.status = status;
    }

    @PrePersist
    public void onPrePersist() {
        if (this.timestamp == null) {
            this.timestamp = Instant.now();
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getPrincipal() {
        return principal;
    }

    public void setPrincipal(String principal) {
        this.principal = principal;
    }

    public String getAction() {
        return action;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getEntityName() {
        return entityName;
    }

    public void setEntityName(String entityName) {
        this.entityName = entityName;
    }

    public String getEntityId() {
        return entityId;
    }

    public void setEntityId(String entityId) {
        this.entityId = entityId;
    }

    public String getIpAddress() {
        return ipAddress;
    }

    public void setIpAddress(String ipAddress) {
        this.ipAddress = ipAddress;
    }

    public String getPayloadDelta() {
        return payloadDelta;
    }

    public void setPayloadDelta(String payloadDelta) {
        this.payloadDelta = payloadDelta;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Instant getTimestamp() {
        return timestamp;
    }

    public void setTimestamp(Instant timestamp) {
        this.timestamp = timestamp;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AuditLogEntity that = (AuditLogEntity) o;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }

    @Override
    public String toString() {
        return "AuditLogEntity{" +
                "id=" + id +
                ", principal='" + principal + '\'' +
                ", action='" + action + '\'' +
                ", entityName='" + entityName + '\'' +
                ", entityId='" + entityId + '\'' +
                ", ipAddress='" + ipAddress + '\'' +
                ", status='" + status + '\'' +
                ", timestamp=" + timestamp +
                '}';
    }
}
