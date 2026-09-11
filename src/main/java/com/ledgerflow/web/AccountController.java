package com.ledgerflow.web;

import com.ledgerflow.domain.Account;
import com.ledgerflow.service.BalanceService;
import com.ledgerflow.web.dto.AccountResponse;
import com.ledgerflow.web.dto.BalanceResponse;
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

    @GetMapping("/{id}")
    public AccountResponse getAccount(@PathVariable Long id) {
        Account account = balanceService.getAccount(id);
        return AccountResponse.from(account);
    }

    @GetMapping("/{id}/balance")
    public BalanceResponse getBalance(@PathVariable Long id) {
        Account account = balanceService.getAccount(id);
        return BalanceResponse.from(account);
    }
}
