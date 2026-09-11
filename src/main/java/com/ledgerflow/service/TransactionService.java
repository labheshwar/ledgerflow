package com.ledgerflow.service;

import com.ledgerflow.domain.Entry;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.repository.EntryRepository;
import com.ledgerflow.repository.TransactionRepository;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.stereotype.Service;

@Service
public class TransactionService {

    private final TransactionRepository transactionRepository;
    private final EntryRepository entryRepository;

    public TransactionService(TransactionRepository transactionRepository, EntryRepository entryRepository) {
        this.transactionRepository = transactionRepository;
        this.entryRepository = entryRepository;
    }

    public List<Transaction> listTransactions() {
        return transactionRepository.findAllByOrderByCreatedAtDesc();
    }

    public Transaction getTransaction(Long id) {
        return transactionRepository.findById(id)
                .orElseThrow(() -> new NoSuchElementException("No transaction with id " + id));
    }

    public List<Entry> getEntries(Long transactionId) {
        return entryRepository.findByTransactionId(transactionId);
    }
}
