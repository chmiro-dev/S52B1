package com.bank.security.service;

import com.bank.security.domain.OutboxMessage;
import com.bank.security.domain.OutboxStatus;
import com.bank.security.event.ChangeDataPublisher;
import com.bank.security.repository.OutboxRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Poller and dispatcher service responsible for fetching PENDING outbox
 * messages
 * and dispatching them through the registered ChangeDataPublisher SPI.
 */
@ApplicationScoped
public class OutboxService {

    private static final Logger LOGGER = Logger.getLogger(OutboxService.class.getName());

    @Inject
    private OutboxRepository outboxRepository;

    @Inject
    private ChangeDataPublisher changeDataPublisher;

    public OutboxService() {
    }

    public OutboxService(OutboxRepository outboxRepository, ChangeDataPublisher changeDataPublisher) {
        this.outboxRepository = outboxRepository;
        this.changeDataPublisher = changeDataPublisher;
    }

    /**
     * Polls a batch of pending outbox messages and attempts to publish each event.
     * Updates message status to PROCESSED on success or FAILED on error.
     *
     * @param batchSize Maximum number of outbox entries to process in a single
     *                  invocation.
     * @return Number of successfully processed events.
     */
    public int processPendingOutboxMessages(int batchSize) {
        List<OutboxMessage> pendingMessages = outboxRepository.findPendingMessages(batchSize);
        int successCount = 0;

        for (OutboxMessage message : pendingMessages) {
            boolean dispatched = dispatchSingleMessage(message);
            if (dispatched) {
                successCount++;
            }
        }

        return successCount;
    }

    /**
     * Dispatches an individual outbox message and updates its state atomically.
     */
    @Transactional
    public boolean dispatchSingleMessage(OutboxMessage message) {
        try {
            changeDataPublisher.publish(
                    message.getAggregateType(),
                    message.getAggregateId(),
                    message.getEventType(),
                    message.getPayload());

            outboxRepository.updateStatus(message.getId(), OutboxStatus.PROCESSED);
            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to dispatch outbox message ID: " + message.getId(), e);
            outboxRepository.updateStatus(message.getId(), OutboxStatus.FAILED);
            return false;
        }
    }
}