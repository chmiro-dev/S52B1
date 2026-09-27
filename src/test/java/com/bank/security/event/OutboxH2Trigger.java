package com.bank.security.event;

import org.h2.api.Trigger;
import java.sql.Connection;
import java.sql.SQLException;

public class OutboxH2Trigger implements Trigger {

    private static ChangeDataPublisher publisher;

    public static void setPublisher(ChangeDataPublisher customPublisher) {
        publisher = customPublisher;
    }

    @Override
    public void init(Connection conn, String schemaName, String triggerName, 
                     String tableName, boolean before, int type) throws SQLException {
        // Trigger initialization logic
    }

    @Override
    public void fire(Connection conn, Object[] oldRow, Object[] newRow) throws SQLException {
        if (newRow == null || publisher == null) {
            return;
        }

        // Mapping row columns from the outbox table schema:
        // [0: id, 1: aggregate_type, 2: aggregate_id, 3: event_type, 4: payload, 5: status, ...]
        String aggregateType = (String) newRow[1];
        String aggregateId   = (String) newRow[2];
        String eventType     = (String) newRow[3];
        String payload       = (String) newRow[4];

        publisher.publish(aggregateType, aggregateId, eventType, payload);
    }

    @Override
    public void close() throws SQLException {}

    @Override
    public void remove() throws SQLException {}
}