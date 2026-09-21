package com.ledgerflow.service;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.BankAccount;
import com.ledgerflow.exception.MasterDataException;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.BankAccountRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.time.OffsetDateTime;
import java.util.NoSuchElementException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Bank accounts: master data like a contact or an item, not a document -- there is no draft, send or void here. */
@Service
public class BankAccountService {

    private final BankAccountRepository bankAccountRepository;
    private final AccountRepository accountRepository;

    public BankAccountService(BankAccountRepository bankAccountRepository, AccountRepository accountRepository) {
        this.bankAccountRepository = bankAccountRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional(readOnly = true)
    public Page<BankAccount> search(String q, boolean includeArchived, Pageable pageable) {
        return bankAccountRepository.search(q, includeArchived, pageable);
    }

    @Transactional(readOnly = true)
    public BankAccount get(Long id) {
        return require(id);
    }

    @Transactional
    public BankAccount create(BankAccountDraft draft) {
        BankAccount bankAccount = new BankAccount();
        bankAccount.setOrgId(TenantContext.require());
        apply(bankAccount, draft);
        return bankAccountRepository.save(bankAccount);
    }

    @Transactional
    public BankAccount update(Long id, BankAccountDraft draft) {
        BankAccount bankAccount = require(id);
        apply(bankAccount, draft);
        return bankAccountRepository.save(bankAccount);
    }

    @Transactional
    public BankAccount archive(Long id) {
        BankAccount bankAccount = require(id);
        bankAccount.setArchivedAt(OffsetDateTime.now());
        return bankAccountRepository.save(bankAccount);
    }

    @Transactional
    public BankAccount restore(Long id) {
        BankAccount bankAccount = require(id);
        bankAccount.setArchivedAt(null);
        return bankAccountRepository.save(bankAccount);
    }

    private void apply(BankAccount bankAccount, BankAccountDraft draft) {
        Account account = requireAccount(draft.accountId());
        bankAccount.setAccountId(account.getId());
        bankAccount.setCurrency(account.getCurrency());
        bankAccount.setName(requireName(draft.name()));
        bankAccount.setAccountNumberLast4(blankToNull(draft.accountNumberLast4()));
    }

    private Account requireAccount(Long accountId) {
        Account account = accountRepository
                .findById(accountId)
                .orElseThrow(() -> new NoSuchElementException("No account with id " + accountId));
        if (!account.isPostable()) {
            throw new MasterDataException(
                    "ACCOUNT_NOT_POSTABLE", "%s is a heading and cannot back a bank account".formatted(account.getName()));
        }
        if (account.isArchived()) {
            throw new MasterDataException(
                    "ACCOUNT_ARCHIVED", "%s is archived and cannot back a bank account".formatted(account.getName()));
        }
        return account;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new MasterDataException("INVALID_NAME", "A bank account needs a name");
        }
        return name.trim();
    }

    private String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }

    private BankAccount require(Long id) {
        return bankAccountRepository.findById(id).orElseThrow(() -> new NoSuchElementException("No bank account with id " + id));
    }
}
