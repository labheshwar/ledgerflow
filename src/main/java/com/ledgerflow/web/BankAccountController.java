package com.ledgerflow.web;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.BankAccount;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.service.BankAccountService;
import com.ledgerflow.service.StatementImportService;
import com.ledgerflow.web.dto.BankAccountRequest;
import com.ledgerflow.web.dto.BankAccountResponse;
import com.ledgerflow.web.dto.ColumnMappingRequest;
import com.ledgerflow.web.dto.PagedResponse;
import com.ledgerflow.web.dto.StatementImportResponse;
import com.ledgerflow.web.dto.StatementLineResponse;
import com.ledgerflow.web.dto.UploadedStatementResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/bank-accounts")
public class BankAccountController {

    private static final Set<String> SORTABLE = Set.of("name", "createdAt", "updatedAt");

    private final BankAccountService bankAccountService;
    private final StatementImportService statementImportService;
    private final AccountRepository accountRepository;

    public BankAccountController(
            BankAccountService bankAccountService,
            StatementImportService statementImportService,
            AccountRepository accountRepository) {
        this.bankAccountService = bankAccountService;
        this.statementImportService = statementImportService;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public PagedResponse<BankAccountResponse> listBankAccounts(
            @RequestParam(required = false) String q,
            @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {

        Pageable sorted = SortWhitelist.apply(pageable, SORTABLE, Sort.by("name").ascending());
        Page<BankAccount> page = bankAccountService.search(q, includeArchived, sorted);
        return PagedResponse.from(page, this::toResponse);
    }

    @GetMapping("/{id}")
    public BankAccountResponse getBankAccount(@PathVariable Long id) {
        return toResponse(bankAccountService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public BankAccountResponse createBankAccount(@Valid @RequestBody BankAccountRequest request) {
        return toResponse(bankAccountService.create(request.toDraft()));
    }

    @PutMapping("/{id}")
    public BankAccountResponse updateBankAccount(@PathVariable Long id, @Valid @RequestBody BankAccountRequest request) {
        return toResponse(bankAccountService.update(id, request.toDraft()));
    }

    @PostMapping("/{id}/archive")
    public BankAccountResponse archiveBankAccount(@PathVariable Long id) {
        return toResponse(bankAccountService.archive(id));
    }

    @PostMapping("/{id}/restore")
    public BankAccountResponse restoreBankAccount(@PathVariable Long id) {
        return toResponse(bankAccountService.restore(id));
    }

    /** Step 1 of the wizard: stores the file and hands back its own header row for the mapping step. */
    @PostMapping("/{id}/imports")
    @ResponseStatus(HttpStatus.CREATED)
    public UploadedStatementResponse uploadStatement(@PathVariable Long id, @RequestParam("file") MultipartFile file) {
        try {
            String filename = file.getOriginalFilename() == null ? "statement.csv" : file.getOriginalFilename();
            return UploadedStatementResponse.from(statementImportService.upload(id, filename, file.getBytes()));
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read the uploaded file", e);
        }
    }

    @GetMapping("/{id}/imports")
    public PagedResponse<StatementImportResponse> listImports(
            @PathVariable Long id, @ParameterObject @PageableDefault(size = 25) Pageable pageable) {
        return PagedResponse.from(statementImportService.listForBankAccount(id, pageable), StatementImportResponse::from);
    }

    @GetMapping("/{id}/imports/{importId}")
    public StatementImportResponse getImport(@PathVariable Long id, @PathVariable Long importId) {
        return StatementImportResponse.from(statementImportService.get(importId));
    }

    /** Re-read from the stored file on demand, so a page refresh on the mapping step never loses them. */
    @GetMapping("/{id}/imports/{importId}/headers")
    public List<String> getImportHeaders(@PathVariable Long id, @PathVariable Long importId) {
        return statementImportService.headersFor(importId);
    }

    /** Step 2 of the wizard: saves the column mapping and queues the worker job that computes the preview. */
    @PostMapping("/{id}/imports/{importId}/preview")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public StatementImportResponse requestPreview(
            @PathVariable Long id, @PathVariable Long importId, @Valid @RequestBody ColumnMappingRequest request) {
        return StatementImportResponse.from(statementImportService.requestPreview(importId, request.toMapping()));
    }

    /** Step 3: the rows the worker staged, not yet real until committed. */
    @GetMapping("/{id}/imports/{importId}/lines")
    public PagedResponse<StatementLineResponse> previewLines(
            @PathVariable Long id, @PathVariable Long importId, @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return PagedResponse.from(statementImportService.previewLines(importId, pageable), StatementLineResponse::from);
    }

    /** Step 4: makes the staged rows real. */
    @PostMapping("/{id}/imports/{importId}/commit")
    public StatementImportResponse commitImport(@PathVariable Long id, @PathVariable Long importId) {
        return StatementImportResponse.from(statementImportService.commit(importId));
    }

    /** The statement itself, once at least one import has been committed. */
    @GetMapping("/{id}/lines")
    public PagedResponse<StatementLineResponse> committedLines(
            @PathVariable Long id, @ParameterObject @PageableDefault(size = 50) Pageable pageable) {
        return PagedResponse.from(statementImportService.committedLines(id, pageable), StatementLineResponse::from);
    }

    private BankAccountResponse toResponse(BankAccount bankAccount) {
        return BankAccountResponse.from(bankAccount, accountName(bankAccount.getAccountId()));
    }

    private String accountName(Long accountId) {
        return accountRepository
                .findById(accountId)
                .map(Account::getName)
                .orElseThrow(() -> new NoSuchElementException("No account with id " + accountId));
    }
}
