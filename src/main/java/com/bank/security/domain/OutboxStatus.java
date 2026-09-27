package com.bank.security.domain;

/**
 * Represents the lifecycle processing state of a Transactional Outbox record.
 */
public enum OutboxStatus {
    /**
     * Event recorded in database; pending publication by CDC trigger or background poller.
     */
    PENDING,

    /**
     * Event successfully dispatched to target downstream subscribers or message brokers.
     */
    PROCESSED,

    /**
     * Event publication failed after maximum retry attempts.
     */
    FAILED
}