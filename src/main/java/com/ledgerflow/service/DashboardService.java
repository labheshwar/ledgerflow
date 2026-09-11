package com.ledgerflow.service;

import com.ledgerflow.domain.Account;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.ReconciliationBatchRepository;
import com.ledgerflow.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final AccountRepository accountRepository;
    private final TransactionRepository transactionRepository;
    private final ReconciliationBatchRepository batchRepository;

    public DashboardService(
            AccountRepository accountRepository,
            TransactionRepository transactionRepository,
            ReconciliationBatchRepository batchRepository) {
        this.accountRepository = accountRepository;
        this.transactionRepository = transactionRepository;
        this.batchRepository = batchRepository;
    }

    public DashboardSummary getSummary() {
        var accounts = accountRepository.findAll();
        BigDecimal totalLedgerBalance = accounts.stream()
                .map(Account::getBalance)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        OffsetDateTime startOfToday = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        long postingsToday = transactionRepository.countByCreatedAtAfter(startOfToday);

        return new DashboardSummary(
                accounts.size(),
                totalLedgerBalance,
                postingsToday,
                batchRepository.findFirstByOrderByTriggeredAtDesc().orElse(null));
    }
}
