package com.ledgerflow.web;

import com.ledgerflow.domain.Transaction;
import com.ledgerflow.repository.TransactionRepository;
import com.ledgerflow.domain.EntryType;
import com.ledgerflow.money.Money;
import com.ledgerflow.service.JournalBuilder;
import com.ledgerflow.service.OrganizationService;
import com.ledgerflow.service.PostingService;
import com.ledgerflow.service.ReversalService;
import com.ledgerflow.service.TransactionService;
import com.ledgerflow.web.dto.EntryRequest;
import com.ledgerflow.web.dto.PagedResponse;
import com.ledgerflow.web.dto.PostTransactionRequest;
import com.ledgerflow.web.dto.ReverseTransactionRequest;
import com.ledgerflow.web.dto.TransactionDetailResponse;
import com.ledgerflow.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private static final Set<String> SORTABLE =
            Set.of("createdAt", "txnDate", "description", "status", "idempotencyKey");

    private final PostingService postingService;
    private final OrganizationService organizationService;
    private final TransactionService transactionService;
    private final ReversalService reversalService;
    private final TransactionRepository transactionRepository;

    public TransactionController(
            PostingService postingService,
            OrganizationService organizationService,
            TransactionService transactionService,
            ReversalService reversalService,
            TransactionRepository transactionRepository) {
        this.postingService = postingService;
        this.organizationService = organizationService;
        this.transactionService = transactionService;
        this.reversalService = reversalService;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse post(@Valid @RequestBody PostTransactionRequest request) {
        // Both defaults are resolved here rather than deeper down, so that a
        // caller who does specify them is never second-guessed.
        String currency = request.currency() == null ? organizationService.baseCurrency() : request.currency();
        LocalDate txnDate = request.txnDate() == null ? LocalDate.now(ZoneOffset.UTC) : request.txnDate();

        JournalBuilder journal = JournalBuilder.forDate(txnDate)
                .withIdempotencyKey(request.idempotencyKey())
                .describedAs(request.description());

        for (EntryRequest line : request.entries()) {
            Money amount = Money.of(line.amount(), currency);
            if (line.entryType() == EntryType.DEBIT) {
                journal.debit(line.accountId(), amount);
            } else {
                journal.credit(line.accountId(), amount);
            }
        }

        Transaction transaction = postingService.post(journal.build());
        return TransactionResponse.from(transaction);
    }

    @GetMapping
    public PagedResponse<TransactionResponse> listTransactions(
            @RequestParam(required = false) String q,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {

        Pageable sorted = SortWhitelist.apply(pageable, SORTABLE, Sort.by("createdAt").descending());
        return PagedResponse.from(transactionRepository.search(q, sorted), TransactionResponse::from);
    }

    @GetMapping("/{id}")
    public TransactionDetailResponse getTransaction(@PathVariable Long id) {
        Transaction transaction = transactionService.getTransaction(id);
        return TransactionDetailResponse.from(
                transaction,
                transactionService.getEntries(id),
                transactionService.reversedBy(id).orElse(null));
    }

    /**
     * Posts the mirror image of an existing transaction: same accounts and
     * amounts, every DEBIT and CREDIT swapped. Idempotent on the original's
     * id -- reversing the same transaction twice returns the first reversal.
     */
    @PostMapping("/{id}/reverse")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse reverse(@PathVariable Long id, @Valid @RequestBody(required = false) ReverseTransactionRequest request) {
        ReverseTransactionRequest body = request != null ? request : new ReverseTransactionRequest(null, null);
        Transaction reversal = reversalService.reverse(id, body.reversalDate(), body.reason());
        return TransactionResponse.from(reversal);
    }
}
