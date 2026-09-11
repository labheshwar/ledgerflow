package com.ledgerflow.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.EntryRepository;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BalanceServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private EntryRepository entryRepository;

    private BalanceService balanceService;

    @BeforeEach
    void setUp() {
        balanceService = new BalanceService(accountRepository, entryRepository);
    }

    @Test
    void foldsEntriesIntoRunningBalanceNewestFirst() {
        Account account = account(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(entryRepository.findByAccountIdOrderByCreatedAtAsc(1L))
                .thenReturn(List.of(
                        entry(10L, 100L, EntryType.DEBIT, new BigDecimal("100.00")),
                        entry(11L, 101L, EntryType.CREDIT, new BigDecimal("30.00")),
                        entry(12L, 102L, EntryType.DEBIT, new BigDecimal("5.00"))));

        List<LedgerEntry> entries = balanceService.getLedgerEntries(1L);

        assertThat(entries).extracting(LedgerEntry::id).containsExactly(12L, 11L, 10L);
        assertThat(entries.get(0).runningBalance()).isEqualByComparingTo("75.00");
        assertThat(entries.get(1).runningBalance()).isEqualByComparingTo("70.00");
        assertThat(entries.get(2).runningBalance()).isEqualByComparingTo("100.00");
    }

    @Test
    void returnsEmptyListWhenAccountHasNoEntries() {
        Account account = account(1L);
        when(accountRepository.findById(1L)).thenReturn(Optional.of(account));
        when(entryRepository.findByAccountIdOrderByCreatedAtAsc(1L)).thenReturn(List.of());

        assertThat(balanceService.getLedgerEntries(1L)).isEmpty();
    }

    @Test
    void throwsWhenAccountDoesNotExist() {
        when(accountRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> balanceService.getLedgerEntries(99L))
                .isInstanceOf(AccountNotFoundException.class);
    }

    private static Account account(Long id) {
        Account account = new Account();
        setField(account, "id", id);
        return account;
    }

    private static Entry entry(Long id, Long transactionId, EntryType type, BigDecimal amount) {
        Transaction transaction = new Transaction();
        setField(transaction, "id", transactionId);

        Entry entry = new Entry();
        setField(entry, "id", id);
        setField(entry, "createdAt", OffsetDateTime.now());
        entry.setTransaction(transaction);
        entry.setEntryType(type);
        entry.setAmount(amount);
        return entry;
    }

    private static void setField(Object target, String fieldName, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(fieldName);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException e) {
            throw new RuntimeException(e);
        }
    }
}
