package com.bank.security.service;

import com.bank.security.domain.AccountEntity;
import com.bank.security.domain.AccountStatus;
import com.bank.security.domain.EntryType;
import com.bank.security.domain.LedgerEntry;
import com.bank.security.domain.OutboxMessage;
import com.bank.security.repository.AccountRepository;
import com.bank.security.repository.LedgerEntryRepository;
import com.bank.security.repository.OutboxRepository;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * Service orchestrating double-entry ledger transactions, account balance
 * checks,
 * and atomic Transactional Outbox event generation.
 */
@ApplicationScoped
public class LedgerService {

    @Inject
    private AccountRepository accountRepository;

    @Inject
    private LedgerEntryRepository ledgerEntryRepository;

    @Inject
    private OutboxRepository outboxRepository;

    public LedgerService() {
    }

    public LedgerService(AccountRepository accountRepository,
            LedgerEntryRepository ledgerEntryRepository,
            OutboxRepository outboxRepository) {
        this.accountRepository = accountRepository;
        this.ledgerEntryRepository = ledgerEntryRepository;
        this.outboxRepository = outboxRepository;
    }

    /**
     * Executes a double-entry funds transfer between two accounts within a single
     * JTA transaction.
     * Enforces debits = credits, account status constraints, and non-sufficient
     * funds safeguards.
     *
     * @param sourceAccountNumber      Account to debit (funds removed)
     * @param destinationAccountNumber Account to credit (funds added)
     * @param amount                   Positive monetary value to transfer
     * @param description              Transaction description
     * @return Generated transaction ID string
     */

    @Transactional
    public String transferFunds(String sourceAccountNumber, String destinationAccountNumber,
            BigDecimal amount, String description) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Transfer amount must be strictly positive");
        }

        if (sourceAccountNumber.equals(destinationAccountNumber)) {
            throw new IllegalArgumentException("Source and destination accounts must be distinct");
        }

        // Fetch accounts using pessimistic write locks to prevent concurrent balance
        // race conditions
        AccountEntity sourceAccount = accountRepository.findByAccountNumberWithLock(sourceAccountNumber)
                .orElseThrow(
                        () -> new IllegalArgumentException("Source account not found: " + sourceAccountNumber));

        AccountEntity destinationAccount = accountRepository.findByAccountNumberWithLock(destinationAccountNumber)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Destination account not found: " + destinationAccountNumber));

        // Validate account operational status
        validateAccountStatus(sourceAccount, "Source");
        validateAccountStatus(destinationAccount, "Destination");

        // Validate sufficient funds on source account
        if (sourceAccount.getBalance().compareTo(amount) < 0) {
            throw new IllegalStateException("Insufficient funds in account: " + sourceAccountNumber);
        }

        String transactionId = UUID.randomUUID().toString();

        // Update balances: Debit source (-), Credit destination (+)
        sourceAccount.setBalance(sourceAccount.getBalance().subtract(amount));
        destinationAccount.setBalance(destinationAccount.getBalance().add(amount));

        accountRepository.save(sourceAccount);
        accountRepository.save(destinationAccount);

        // Record paired double-entry ledger records (Debits = Credits)
        LedgerEntry debitEntry = new LedgerEntry();
        debitEntry.setTransactionId(transactionId);
        debitEntry.setAccount(sourceAccount);
        debitEntry.setEntryType(EntryType.DEBIT);
        debitEntry.setAmount(amount);
        debitEntry.setDescription(description);

        LedgerEntry creditEntry = new LedgerEntry();
        creditEntry.setTransactionId(transactionId);
        creditEntry.setAccount(destinationAccount);
        creditEntry.setEntryType(EntryType.CREDIT);
        creditEntry.setAmount(amount);
        creditEntry.setDescription(description);

        // Perform balance invariant assertion before persisting
        validateDoubleEntryBalance(List.of(debitEntry, creditEntry));

        ledgerEntryRepository.save(debitEntry);
        ledgerEntryRepository.save(creditEntry);

        // Construct Outbox event atomically within the same database transaction
        String payload = String.format(
                "{\"transactionId\":\"%s\",\"sourceAccount\":\"%s\",\"destinationAccount\":\"%s\",\"amount\":%s}",
                transactionId, sourceAccountNumber, destinationAccountNumber, amount.toPlainString());

        OutboxMessage outboxMessage = new OutboxMessage(
                "LEDGER_TRANSACTION",
                transactionId,
                "FUNDS_TRANSFERRED",
                payload);
        outboxRepository.save(outboxMessage);

        return transactionId;
    }

    private void validateAccountStatus(AccountEntity account, String role) {
        if (account.getStatus() != AccountStatus.ACTIVE) {
            throw new IllegalStateException(role + " account " + account.getAccountNumber()
                    + " is not ACTIVE (Status: " + account.getStatus() + ")");
        }
    }

    /**
     * Programmatically validates the double-entry accounting rule sum(Debits) ==
     * sum(Credits).
     */
    private void validateDoubleEntryBalance(List<LedgerEntry> entries) {
        BigDecimal totalDebits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.DEBIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal totalCredits = entries.stream()
                .filter(e -> e.getEntryType() == EntryType.CREDIT)
                .map(LedgerEntry::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        if (totalDebits.compareTo(totalCredits) != 0) {
            throw new IllegalStateException(
                    "Unbalanced transaction error: Total Debits (" + totalDebits
                            + ") do not equal Total Credits (" + totalCredits + ")");
        }
    }
}