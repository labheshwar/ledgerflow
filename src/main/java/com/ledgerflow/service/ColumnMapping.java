package com.ledgerflow.service;

/**
 * Which of the CSV's own header names holds the date, description and
 * amount a statement line needs. {@code externalIdColumn} is optional --
 * see {@code StatementImportService} for what happens without one.
 */
public record ColumnMapping(String dateColumn, String descriptionColumn, String amountColumn, String externalIdColumn) {}
