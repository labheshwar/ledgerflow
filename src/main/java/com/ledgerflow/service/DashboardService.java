package com.ledgerflow.service;

import com.ledgerflow.repository.AccountBalanceQueries;
import com.ledgerflow.repository.TransactionRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import org.springframework.stereotype.Service;

@Service
public class DashboardService {

    private final AccountBalanceQueries accountBalanceQueries;
    private final TransactionRepository transactionRepository;

    public DashboardService(AccountBalanceQueries accountBalanceQueries, TransactionRepository transactionRepository) {
        this.accountBalanceQueries = accountBalanceQueries;
        this.transactionRepository = transactionRepository;
    }

    public DashboardSummary getSummary() {
        // Summed in the database, and in the reporting currency. Adding up
        // account-currency balances would put dollars and euros in the same
        // total; folding them in Java would mean loading every account to
        // add up numbers Postgres can add up itself.
        BigDecimal totalLedgerBalance = accountBalanceQueries.totalBaseBalance();
        long accountCount = accountBalanceQueries.count();

        OffsetDateTime startOfToday = OffsetDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.DAYS);
        long postingsToday = transactionRepository.countByCreatedAtAfter(startOfToday);

        return new DashboardSummary(accountCount, totalLedgerBalance, postingsToday);
    }
}
