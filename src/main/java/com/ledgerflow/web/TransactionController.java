package com.ledgerflow.web;

import com.ledgerflow.domain.Transaction;
import com.ledgerflow.service.EntryLine;
import com.ledgerflow.service.PostingCommand;
import com.ledgerflow.service.PostingService;
import com.ledgerflow.web.dto.PostTransactionRequest;
import com.ledgerflow.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/transactions")
public class TransactionController {

    private final PostingService postingService;

    public TransactionController(PostingService postingService) {
        this.postingService = postingService;
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
}
