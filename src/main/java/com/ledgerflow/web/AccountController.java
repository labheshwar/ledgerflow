package com.ledgerflow.web;

import com.ledgerflow.domain.Account;
import com.ledgerflow.service.BalanceService;
import com.ledgerflow.web.dto.AccountResponse;
import com.ledgerflow.web.dto.BalanceResponse;
import com.ledgerflow.web.dto.LedgerEntryResponse;
import java.util.List;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/accounts")
public class AccountController {

    private final BalanceService balanceService;

    public AccountController(BalanceService balanceService) {
        this.balanceService = balanceService;
    }

    @GetMapping
    public List<AccountResponse> listAccounts() {
        return balanceService.listAccounts().stream().map(AccountResponse::from).toList();
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
