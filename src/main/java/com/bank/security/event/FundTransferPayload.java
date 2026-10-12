package com.bank.security.event;

import java.math.BigDecimal;

/**
 * Structured payload representation for fund transfer outbox events.
 */
public record FundTransferPayload(
        String transactionId,
        String sourceAccount,
        String destinationAccount,
        BigDecimal amount) {
}