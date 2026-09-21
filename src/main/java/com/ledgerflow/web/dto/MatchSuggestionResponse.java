package com.ledgerflow.web.dto;

import com.ledgerflow.service.MatchKind;
import com.ledgerflow.service.MatchSuggestion;
import java.math.BigDecimal;
import java.time.LocalDate;

public record MatchSuggestionResponse(MatchKind kind, Long id, String label, LocalDate date, BigDecimal amount, double score) {

    public static MatchSuggestionResponse from(MatchSuggestion suggestion) {
        return new MatchSuggestionResponse(
                suggestion.kind(), suggestion.id(), suggestion.label(), suggestion.date(), suggestion.amount(), suggestion.score());
    }
}
