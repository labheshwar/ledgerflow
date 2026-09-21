package com.ledgerflow.service;

import com.ledgerflow.domain.StatementImport;
import java.util.List;

/** What an upload hands back immediately: the import row and the CSV's own header names, for the mapping step. */
public record UploadedStatement(StatementImport statementImport, List<String> headers) {}
