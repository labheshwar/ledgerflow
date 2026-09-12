package com.ledgerflow.web;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.service.BalanceService;
import com.ledgerflow.web.dto.AccountResponse;
import com.ledgerflow.web.dto.BalanceResponse;
import com.ledgerflow.web.dto.LedgerEntryResponse;
import com.ledgerflow.web.dto.PagedResponse;
import java.util.List;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private static final Set<String> SORTABLE = Set.of("name", "type", "balance", "currency", "updatedAt");

    private final BalanceService balanceService;
    private final AccountRepository accountRepository;

    public AccountController(BalanceService balanceService, AccountRepository accountRepository) {
        this.balanceService = balanceService;
        this.accountRepository = accountRepository;
    }

    @GetMapping
    public PagedResponse<AccountResponse> listAccounts(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) AccountType type,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {

        Pageable sorted = SortWhitelist.apply(pageable, SORTABLE, Sort.by("name").ascending());
        return PagedResponse.from(accountRepository.search(q, type, sorted), AccountResponse::from);
    }

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable Long id) {
        Account account = balanceService.getAccount(id);
        return AccountResponse.from(account);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse getBalance(@PathVariable Long id) {
        return BalanceResponse.from(balanceService.getCachedBalance(id));
    }

    @GetMapping("/{id}/entries")
    public List<LedgerEntryResponse> getEntries(@PathVariable Long id) {
        return balanceService.getLedgerEntries(id).stream().map(LedgerEntryResponse::from).toList();
    }
}
