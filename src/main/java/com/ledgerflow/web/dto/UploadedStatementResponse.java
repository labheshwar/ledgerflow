package com.ledgerflow.web.dto;

import com.ledgerflow.service.UploadedStatement;
import java.util.List;

public record UploadedStatementResponse(Long importId, List<String> headers) {

    public static UploadedStatementResponse from(UploadedStatement uploaded) {
        return new UploadedStatementResponse(uploaded.statementImport().getId(), uploaded.headers());
    }
}
