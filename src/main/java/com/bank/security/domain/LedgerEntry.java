package com.bank.security.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "ledger_entries")
public class LedgerEntry implements Serializable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "transaction_id", nullable = false, length = 64)
    private String transactionId;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "account_id", nullable = false)
    private AccountEntity account;

    @Enumerated(EnumType.STRING)
    @Column(name = "entry_type", nullable = false, length = 10)
    private EntryType entryType;

    @Column(name = "amount", nullable = false, precision = 19, scale = 4)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "USD";

    @Column(name = "balance_after", precision = 19, scale = 4)
    private BigDecimal balanceAfter;

    @Column(name = "description", length = 255)
    private String description;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public LedgerEntry() {
    }

    public LedgerEntry(String transactionId, AccountEntity account, EntryType entryType, BigDecimal amount, String currency, String description) {
        this.transactionId = transactionId;
        this.account = account;
        this.entryType = entryType;
        this.amount = amount;
        this.currency = currency != null ? currency : "USD";
        this.description = description;
    }

    public LedgerEntry(String transactionId, AccountEntity account, EntryType entryType, BigDecimal amount, String currency, BigDecimal balanceAfter, String description) {
        this.transactionId = transactionId;
        this.account = account;
        this.entryType = entryType;
        this.amount = amount;
        this.currency = currency != null ? currency : "USD";
        this.balanceAfter = balanceAfter;
        this.description = description;
    }

    @PrePersist
    public void onPrePersist() {
        if (this.createdAt == null) {
            this.createdAt = Instant.now();
        }
        if (this.currency == null && this.account != null) {
            this.currency = this.account.getCurrency();
        } else if (this.currency == null) {
            this.currency = "USD";
        }
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTransactionId() {
        return transactionId;
    }

    public void setTransactionId(String transactionId) {
        this.transactionId = transactionId;
    }

    public AccountEntity getAccount() {
        return account;
    }

    public void setAccount(AccountEntity account) {
        this.account = account;
    }

    public EntryType getEntryType() {
        return entryType;
    }

    public void setEntryType(EntryType entryType) {
        this.entryType = entryType;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public BigDecimal getBalanceAfter() {
        return balanceAfter;
    }

    public void setBalanceAfter(BigDecimal balanceAfter) {
        this.balanceAfter = balanceAfter;
    }

    public String getDescription() {
        return description;
    }

    public void setDescription(String description) {
        this.description = description;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(Instant createdAt) {
        this.createdAt = createdAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        LedgerEntry that = (LedgerEntry) o;
        return Objects.equals(id, that.id) ||
                (id == null && Objects.equals(transactionId, that.transactionId) && Objects.equals(account, that.account) && Objects.equals(entryType, that.entryType));
    }

    @Override
    public int hashCode() {
        return id != null ? Objects.hash(id) : Objects.hash(transactionId, entryType);
    }

    @Override
    public String toString() {
        return "LedgerEntry{" +
                "id=" + id +
                ", transactionId='" + transactionId + '\'' +
                ", entryType=" + entryType +
                ", amount=" + amount +
                ", currency='" + currency + '\'' +
                ", balanceAfter=" + balanceAfter +
                ", description='" + description + '\'' +
                ", createdAt=" + createdAt +
                '}';
    }
}
