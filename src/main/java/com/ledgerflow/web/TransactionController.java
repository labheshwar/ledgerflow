package com.ledgerflow.web;

import com.ledgerflow.domain.Transaction;
import com.ledgerflow.repository.TransactionRepository;
import com.ledgerflow.service.EntryLine;
import com.ledgerflow.service.PostingCommand;
import com.ledgerflow.service.PostingService;
import com.ledgerflow.service.TransactionService;
import com.ledgerflow.web.dto.PagedResponse;
import com.ledgerflow.web.dto.PostTransactionRequest;
import com.ledgerflow.web.dto.TransactionDetailResponse;
import com.ledgerflow.web.dto.TransactionResponse;
import jakarta.validation.Valid;
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

    private static final Set<String> SORTABLE = Set.of("createdAt", "description", "status", "idempotencyKey");

    private final PostingService postingService;
    private final TransactionService transactionService;
    private final TransactionRepository transactionRepository;

    public TransactionController(
            PostingService postingService,
            TransactionService transactionService,
            TransactionRepository transactionRepository) {
        this.postingService = postingService;
        this.transactionService = transactionService;
        this.transactionRepository = transactionRepository;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse post(@Valid @RequestBody PostTransactionRequest request) {
        PostingCommand command = new PostingCommand(
                request.idempotencyKey(),
                request.description(),
                request.entries().stream()
                        .map(e -> new EntryLine(e.accountId(), e.entryType(), e.amount()))
                        .toList());

        Transaction transaction = postingService.post(command);
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
        return TransactionDetailResponse.from(transaction, transactionService.getEntries(id));
    }
}
