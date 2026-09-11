package com.ledgerflow.web;

import com.ledgerflow.domain.Transaction;
import com.ledgerflow.service.EntryLine;
import com.ledgerflow.service.PostingCommand;
import com.ledgerflow.service.PostingService;
import com.ledgerflow.service.TransactionService;
import com.ledgerflow.web.dto.PostTransactionRequest;
import com.ledgerflow.web.dto.TransactionDetailResponse;
import com.ledgerflow.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final PostingService postingService;
    private final TransactionService transactionService;

    public TransactionController(PostingService postingService, TransactionService transactionService) {
        this.postingService = postingService;
        this.transactionService = transactionService;
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
    public List<TransactionResponse> listTransactions() {
        return transactionService.listTransactions().stream().map(TransactionResponse::from).toList();
    }

    @GetMapping("/{id}")
    public TransactionDetailResponse getTransaction(@PathVariable Long id) {
        Transaction transaction = transactionService.getTransaction(id);
        return TransactionDetailResponse.from(transaction, transactionService.getEntries(id));
    }
}
