package com.ledgerflow.web;

import com.ledgerflow.domain.AccountType;
import com.ledgerflow.repository.AccountBalanceQueries;
import com.ledgerflow.repository.AccountWithBalance;
import com.ledgerflow.service.BalanceService;
import com.ledgerflow.service.ChartOfAccountsService;
import com.ledgerflow.web.dto.AccountNodeResponse;
import com.ledgerflow.web.dto.AccountRequest;
import com.ledgerflow.web.dto.AccountResponse;
import com.ledgerflow.web.dto.BalanceResponse;
import com.ledgerflow.web.dto.LedgerEntryResponse;
import com.ledgerflow.web.dto.PagedResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private static final Set<String> SORTABLE =
            Set.of("code", "name", "type", "balance", "currency", "updatedAt");

    private final BalanceService balanceService;
    private final ChartOfAccountsService chartOfAccounts;
    private final AccountBalanceQueries accountBalanceQueries;

    public AccountController(
            BalanceService balanceService,
            ChartOfAccountsService chartOfAccounts,
            AccountBalanceQueries accountBalanceQueries) {
        this.balanceService = balanceService;
        this.chartOfAccounts = chartOfAccounts;
        this.accountBalanceQueries = accountBalanceQueries;
    }

    @GetMapping
    public PagedResponse<AccountResponse> listAccounts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AccountType type,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {

        Pageable sorted = SortWhitelist.apply(pageable, SORTABLE, Sort.by("code").ascending());
        return PagedResponse.from(accountBalanceQueries.search(q, type, sorted), AccountResponse::from);
    }

    /**
     * The chart as a tree. Not paged: a chart of accounts is read whole or not
     * at all -- a page of a tree is a set of orphaned branches.
     */
    @GetMapping("/tree")
    public List<AccountNodeResponse> chart(
            @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived) {
        return chartOfAccounts.chart(includeArchived).stream()
                .map(AccountNodeResponse::from)
                .toList();
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable Long id) {
        AccountWithBalance account = balanceService.getAccount(id);
        return AccountResponse.from(account);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponse createAccount(@Valid @RequestBody AccountRequest request) {
        Long id = chartOfAccounts.create(request.toDraft()).getId();
        // Read back through the balance query so the response has the same
        // shape as every other account read, balance included.
        return AccountResponse.from(balanceService.getAccount(id));
    }

    @PutMapping("/{id}")
    public AccountResponse updateAccount(@PathVariable Long id, @Valid @RequestBody AccountRequest request) {
        chartOfAccounts.update(id, request.toDraft());
        return AccountResponse.from(balanceService.getAccount(id));
    }

    /**
     * Archive rather than delete. POST rather than DELETE, because this does
     * not remove anything -- the account and its history stay exactly where
     * they were.
     */
    @PostMapping("/{id}/archive")
    public AccountResponse archiveAccount(@PathVariable Long id) {
        chartOfAccounts.archive(id);
        return AccountResponse.from(balanceService.getAccount(id));
    }

    @PostMapping("/{id}/restore")
    public AccountResponse restoreAccount(@PathVariable Long id) {
        chartOfAccounts.restore(id);
        return AccountResponse.from(balanceService.getAccount(id));
    }

    /** Only ever succeeds for an account that has never been posted to. */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable Long id) {
        chartOfAccounts.delete(id);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse getBalance(@PathVariable Long id) {
        return BalanceResponse.from(balanceService.getBalance(id));
    }

    @GetMapping("/{id}/entries")
    public List<LedgerEntryResponse> getEntries(@PathVariable Long id) {
        return balanceService.getLedgerEntries(id).stream().map(LedgerEntryResponse::from).toList();
    }
}
