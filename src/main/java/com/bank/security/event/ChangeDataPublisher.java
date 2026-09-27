package com.bank.security.event;

/**
 * Database-agnostic Service Provider Interface (SPI) for publishing
 * Change Data Capture (CDC) outbox events.
 */
public interface ChangeDataPublisher {

    /**
     * Publishes a raw change data event to downstream listeners or message brokers.
     *
     * @param aggregateType The target entity domain (e.g., LEDGER_TRANSACTION)
     * @param aggregateId   The primary key or logical ID of the record
     * @param eventType     The operation name (e.g., TRANSACTION_POSTED)
     * @param payload       The JSON payload of the event
     */
    void publish(String aggregateType, String aggregateId, String eventType, String payload);
}