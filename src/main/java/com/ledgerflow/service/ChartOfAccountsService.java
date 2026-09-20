package com.ledgerflow.service;

import com.ledgerflow.domain.Account;
import com.ledgerflow.domain.AccountType;
import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.exception.AccountNotFoundException;
import com.ledgerflow.exception.ChartOfAccountsException;
import com.ledgerflow.repository.AccountBalanceQueries;
import com.ledgerflow.repository.AccountRepository;
import com.ledgerflow.repository.AccountWithBalance;
import com.ledgerflow.repository.EntryRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.time.OffsetDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The rules of the chart of accounts.
 *
 * Most of this class is refusals, and each one exists because the alternative
 * is a ledger that cannot be explained afterwards. The recurring theme: once
 * an account has entries against it, almost nothing about it may change,
 * because the entries are history and history has to keep meaning what it
 * meant when it was written.
 */
@Service
public class ChartOfAccountsService {

    private final AccountRepository accountRepository;
    private final AccountBalanceQueries accountBalanceQueries;
    private final EntryRepository entryRepository;
    private final OrganizationService organizationService;

    public ChartOfAccountsService(
            AccountRepository accountRepository,
            AccountBalanceQueries accountBalanceQueries,
            EntryRepository entryRepository,
            OrganizationService organizationService) {
        this.accountRepository = accountRepository;
        this.accountBalanceQueries = accountBalanceQueries;
        this.entryRepository = entryRepository;
        this.organizationService = organizationService;
    }

    /**
     * The chart as a tree, headings carrying the total of everything beneath
     * them.
     *
     * @param includeArchived archived accounts are hidden by default -- they
     *        exist only so history stays explicable, and showing them in the
     *        working view is noise.
     */
    @Transactional(readOnly = true)
    public List<AccountNode> chart(boolean includeArchived) {
        List<AccountWithBalance> accounts = accountBalanceQueries.findAllOrderedByCode().stream()
                .filter(account -> includeArchived || !account.archived())
                .toList();
        return AccountNode.treeOf(accounts);
    }

    @Transactional
    public Account create(AccountDraft draft) {
        Long orgId = TenantContext.require();

        Account account = new Account();
        account.setOrgId(orgId);
        account.setCode(normalizeCode(draft.code()));
        account.setName(requireName(draft.name()));
        account.setDescription(draft.description());
        account.setType(requireType(draft.type()));
        account.setCurrency(resolveCurrency(draft.currency()));
        account.setPostable(draft.postable());
        account.setSystemRole(draft.systemRole());
        applyParent(account, draft.parentId());

        return save(account);
    }

    @Transactional
    public Account update(Long accountId, AccountDraft draft) {
        Account account = require(accountId);

        account.setName(requireName(draft.name()));
        account.setDescription(draft.description());
        account.setCode(normalizeCode(draft.code()));

        boolean used = hasEntries(accountId);
        AccountType requestedType = requireType(draft.type());
        if (used && requestedType != account.getType()) {
            // Retyping an account retrospectively moves every entry ever
            // posted to it from one side of the balance sheet to the other,
            // silently restating every report that has already been filed.
            throw new ChartOfAccountsException(
                    "ACCOUNT_IN_USE", "This account has entries, so its type can no longer be changed");
        }
        account.setType(requestedType);

        String requestedCurrency = resolveCurrency(draft.currency());
        if (used && !requestedCurrency.equalsIgnoreCase(account.getCurrency())) {
            // The entries are denominated in the old currency. Changing it
            // would relabel real amounts as a currency they were never in.
            throw new ChartOfAccountsException(
                    "ACCOUNT_IN_USE", "This account has entries, so its currency can no longer be changed");
        }
        account.setCurrency(requestedCurrency);

        if (used && !draft.postable()) {
            throw new ChartOfAccountsException(
                    "ACCOUNT_IN_USE", "This account already has entries, so it cannot be made a heading");
        }
        account.setPostable(draft.postable());
        account.setSystemRole(draft.systemRole());
        applyParent(account, draft.parentId());

        return save(account);
    }

    /**
     * Archiving, never deleting. An account with entries against it is
     * referenced by history; removing it would leave transactions pointing at
     * nothing and a trial balance that cannot be reproduced.
     */
    @Transactional
    public Account archive(Long accountId) {
        Account account = require(accountId);

        if (account.getSystemRole() != null) {
            // The application posts to these by role. Archiving one breaks
            // invoicing at the moment someone next raises an invoice, which
            // is a long way from where the mistake was made.
            throw new ChartOfAccountsException(
                    "SYSTEM_ACCOUNT",
                    "%s is used by the system as %s and cannot be archived"
                            .formatted(account.getName(), account.getSystemRole()));
        }
        if (!childrenOf(accountId).isEmpty()) {
            throw new ChartOfAccountsException(
                    "HAS_CHILDREN", "Archive or move the accounts underneath this one first");
        }

        account.setArchivedAt(OffsetDateTime.now());
        return accountRepository.save(account);
    }

    @Transactional
    public Account restore(Long accountId) {
        Account account = require(accountId);
        account.setArchivedAt(null);
        return accountRepository.save(account);
    }

    /**
     * Deletion is allowed in exactly one case: an account created by mistake
     * that has never been posted to. Anything else archives.
     */
    @Transactional
    public void delete(Long accountId) {
        Account account = require(accountId);

        if (hasEntries(accountId)) {
            throw new ChartOfAccountsException(
                    "ACCOUNT_IN_USE", "This account has entries and can only be archived, not deleted");
        }
        if (account.getSystemRole() != null) {
            throw new ChartOfAccountsException(
                    "SYSTEM_ACCOUNT", "This account is used by the system and cannot be deleted");
        }
        if (!childrenOf(accountId).isEmpty()) {
            throw new ChartOfAccountsException(
                    "HAS_CHILDREN", "Delete or move the accounts underneath this one first");
        }

        accountRepository.delete(account);
    }

    /**
     * The account the application means by a role, for the features that post
     * without asking -- invoicing, bills, tax, FX.
     */
    @Transactional(readOnly = true)
    public Account requireByRole(SystemAccountRole role) {
        return accountRepository
                .findBySystemRole(role)
                .orElseThrow(() -> new ChartOfAccountsException(
                        "MISSING_SYSTEM_ACCOUNT",
                        "This organization has no account set up as " + role));
    }

    private void applyParent(Account account, Long parentId) {
        if (parentId == null) {
            account.setParentId(null);
            return;
        }
        if (parentId.equals(account.getId())) {
            throw new ChartOfAccountsException("INVALID_PARENT", "An account cannot be its own parent");
        }

        Account parent = accountRepository
                .findById(parentId)
                .orElseThrow(() -> new ChartOfAccountsException("INVALID_PARENT", "No account with id " + parentId));

        if (parent.getType() != account.getType()) {
            // A liability nested under assets would be added into the asset
            // subtotal, and the balance sheet would stop balancing.
            throw new ChartOfAccountsException(
                    "INVALID_PARENT",
                    "A %s account cannot sit under a %s heading".formatted(account.getType(), parent.getType()));
        }
        if (hasEntries(parentId)) {
            // The parent's total would then be its own entries plus its
            // children's, and no report could tell the two apart.
            throw new ChartOfAccountsException(
                    "INVALID_PARENT", "%s has entries of its own and cannot become a heading".formatted(parent.getName()));
        }
        if (account.getId() != null && isDescendant(parentId, account.getId())) {
            // Without this the tree becomes a ring: walking upwards never
            // reaches a root, and every report that sums a subtree hangs.
            throw new ChartOfAccountsException(
                    "INVALID_PARENT", "That would put the account underneath itself");
        }

        // A heading's balance is the sum of what is beneath it. Letting it
        // also hold entries directly would double-count them.
        if (parent.isPostable()) {
            parent.setPostable(false);
            accountRepository.save(parent);
        }
        account.setParentId(parentId);
    }

    /** Walks upwards from {@code startId} looking for {@code ancestorId}. */
    private boolean isDescendant(Long startId, Long ancestorId) {
        Set<Long> seen = new HashSet<>();
        Long current = startId;
        while (current != null && seen.add(current)) {
            if (current.equals(ancestorId)) {
                return true;
            }
            current = accountRepository.findById(current).map(Account::getParentId).orElse(null);
        }
        return false;
    }

    private List<Account> childrenOf(Long accountId) {
        return accountRepository.findByParentId(accountId);
    }

    private boolean hasEntries(Long accountId) {
        return entryRepository.existsByAccountId(accountId);
    }

    private Account require(Long accountId) {
        return accountRepository.findById(accountId).orElseThrow(() -> new AccountNotFoundException(accountId));
    }

    private Account save(Account account) {
        try {
            return accountRepository.saveAndFlush(account);
        } catch (DataIntegrityViolationException e) {
            // The unique constraints are per organization, so the same code in
            // a different business is fine and only a genuine collision lands
            // here. Flushing inside the try is what makes it land here at all
            // rather than at commit, where it could not be attributed.
            throw new ChartOfAccountsException(
                    "DUPLICATE_CODE",
                    "Another account already uses code %s or that system role".formatted(account.getCode()));
        }
    }

    private String normalizeCode(String code) {
        if (code == null || code.isBlank()) {
            throw new ChartOfAccountsException("INVALID_CODE", "An account needs a code");
        }
        String trimmed = code.trim();
        if (!trimmed.matches("^[A-Za-z0-9][A-Za-z0-9.\\-]*$")) {
            throw new ChartOfAccountsException(
                    "INVALID_CODE", "A code may contain only letters, digits, dots and dashes");
        }
        return trimmed;
    }

    private String requireName(String name) {
        if (name == null || name.isBlank()) {
            throw new ChartOfAccountsException("INVALID_NAME", "An account needs a name");
        }
        return name.trim();
    }

    private AccountType requireType(AccountType type) {
        if (type == null) {
            throw new ChartOfAccountsException("INVALID_TYPE", "An account needs a type");
        }
        return type;
    }

    /**
     * Defaults to the organization's reporting currency, which is what nearly
     * every account is held in and what a user would otherwise have to pick
     * correctly every single time.
     */
    private String resolveCurrency(String currency) {
        return currency == null || currency.isBlank()
                ? organizationService.baseCurrency()
                : currency.trim().toUpperCase(java.util.Locale.ROOT);
    }
}
