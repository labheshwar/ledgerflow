package com.ledgerflow.service;

import java.math.BigDecimal;

public record DashboardSummary(long totalAccounts, BigDecimal totalLedgerBalance, long postingsToday) {}
