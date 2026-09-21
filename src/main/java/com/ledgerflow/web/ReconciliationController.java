package com.ledgerflow.web;

import com.ledgerflow.service.ReconciliationService;
import com.ledgerflow.web.dto.CategorizeRequest;
import com.ledgerflow.web.dto.MatchEntryRequest;
import com.ledgerflow.web.dto.MatchSuggestionResponse;
import com.ledgerflow.web.dto.PagedResponse;
import com.ledgerflow.web.dto.ReconciliationSummaryResponse;
import com.ledgerflow.web.dto.SettleDocumentRequest;
import com.ledgerflow.web.dto.StatementLineResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * The two-pane workspace: {@code bankAccountId} in the path is for the
 * URL's own clarity, exactly as it is on {@code BankAccountController}'s
 * nested import endpoints -- every action past that point is addressed by
 * the statement line's own id, which already carries which bank account
 * (and, through it, which organization) it belongs to.
 */
@RestController
@RequestMapping("/bank-accounts/{bankAccountId}/reconciliation")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    public ReconciliationController(ReconciliationService reconciliationService) {
        this.reconciliationService = reconciliationService;
    }

    @GetMapping("/summary")
    public ReconciliationSummaryResponse summary(@PathVariable Long bankAccountId) {
        return ReconciliationSummaryResponse.from(reconciliationService.summaryFor(bankAccountId));
    }

    @GetMapping("/lines")
    public PagedResponse<StatementLineResponse> lines(
            @PathVariable Long bankAccountId,
            @RequestParam(required = false) Boolean matched,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {
        return PagedResponse.from(reconciliationService.lines(bankAccountId, matched, pageable), StatementLineResponse::from);
    }

    @GetMapping("/lines/{lineId}/suggestions")
    public List<MatchSuggestionResponse> suggestions(@PathVariable Long bankAccountId, @PathVariable Long lineId) {
        return reconciliationService.suggestionsFor(lineId).stream().map(MatchSuggestionResponse::from).toList();
    }

    @PostMapping("/lines/{lineId}/match")
    public StatementLineResponse match(
            @PathVariable Long bankAccountId, @PathVariable Long lineId, @Valid @RequestBody MatchEntryRequest request) {
        return StatementLineResponse.from(reconciliationService.matchToEntry(lineId, request.entryId()));
    }

    @PostMapping("/lines/{lineId}/settle")
    public StatementLineResponse settle(
            @PathVariable Long bankAccountId, @PathVariable Long lineId, @Valid @RequestBody SettleDocumentRequest request) {
        return StatementLineResponse.from(reconciliationService.settle(lineId, request.documentType(), request.documentId()));
    }

    @PostMapping("/lines/{lineId}/categorize")
    public StatementLineResponse categorize(
            @PathVariable Long bankAccountId, @PathVariable Long lineId, @Valid @RequestBody CategorizeRequest request) {
        return StatementLineResponse.from(reconciliationService.categorize(lineId, request.accountId(), request.description()));
    }

    @PostMapping("/lines/{lineId}/unmatch")
    public StatementLineResponse unmatch(@PathVariable Long bankAccountId, @PathVariable Long lineId) {
        return StatementLineResponse.from(reconciliationService.unmatch(lineId));
    }
}
