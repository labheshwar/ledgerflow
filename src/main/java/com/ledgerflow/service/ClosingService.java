package com.ledgerflow.service;

import com.ledgerflow.domain.SystemAccountRole;
import com.ledgerflow.domain.Transaction;
import com.ledgerflow.exception.ChartOfAccountsException;
import com.ledgerflow.money.Money;
import com.ledgerflow.repository.AccountBalanceQueries;
import com.ledgerflow.repository.AccountWithBalance;
import com.ledgerflow.repository.TransactionRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import org.springframework.stereotype.Service;

/**
 * The year-end closing journal: zeroes every revenue and expense account and
 * moves the net result into Retained Earnings, exactly as a bookkeeper would
 * on paper.
 *
 * The arithmetic is one balance invariant applied twice. Every revenue and
 * expense account gets an entry that brings its own balance to exactly zero;
 * that alone would leave the journal unbalanced by the net of everything
 * just zeroed, so Retained Earnings takes the exact opposite of that net as
 * its own single line. The result is a journal that balances by
 * construction, not by having been checked afterwards -- the same guarantee
 * PostingCommand already gives every other posting.
 *
 * Deliberately not @Transactional, for the same reason ReversalService is
 * not: the only write here is the one PostingService.post() already makes
 * atomically, and wrapping it in a further transaction only recreates the
 * outer-transaction trap the project's plan warns against.
 */
@Service
public class ClosingService {

    private final AccountBalanceQueries accountBalanceQueries;
    private final ChartOfAccountsService chartOfAccounts;
    private final OrganizationService organizationService;
    private final PostingService postingService;
    private final TransactionRepository transactionRepository;

    public ClosingService(
            AccountBalanceQueries accountBalanceQueries,
            ChartOfAccountsService chartOfAccounts,
            OrganizationService organizationService,
            PostingService postingService,
            TransactionRepository transactionRepository) {
        this.accountBalanceQueries = accountBalanceQueries;
        this.chartOfAccounts = chartOfAccounts;
        this.organizationService = organizationService;
        this.postingService = postingService;
        this.transactionRepository = transactionRepository;
    }

    /**
     * @param asOfDate the last day of the fiscal year being closed. The
     *        idempotency key is derived from it, so closing the same year
     *        twice returns the original closing journal rather than double
     *        counting income that has already been moved to equity.
     * @throws ChartOfAccountsException with code MISSING_SYSTEM_ACCOUNT if
     *         the organization has no account marked RETAINED_EARNINGS
     * @throws ChartOfAccountsException with code NOTHING_TO_CLOSE if every
     *         revenue and expense account is already at zero as of that date
     */
    public Transaction closeFiscalYear(LocalDate asOfDate) {
        Long orgId = TenantContext.require();
        String idempotencyKey = "CLOSE:%d:%s".formatted(orgId, asOfDate);

        // Closing a year that was already closed zeroes accounts that are
        // now, correctly, already at zero -- so the "anything to close"
        // check below would misfire on a repeat call before the idempotency
        // key ever gets a chance to short-circuit. Checking for the prior
        // transaction first, the same way PostingService.post() itself
        // would, keeps a repeat close idempotent rather than an error.
        var existing = transactionRepository.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return existing.get();
        }

        String baseCurrency = organizationService.baseCurrency();
        var retainedEarnings = chartOfAccounts.requireByRole(SystemAccountRole.RETAINED_EARNINGS);

        List<AccountWithBalance> toClose = accountBalanceQueries.findPostableRevenueAndExpenseAsOf(asOfDate);
        if (toClose.isEmpty()) {
            throw new ChartOfAccountsException(
                    "NOTHING_TO_CLOSE", "No revenue or expense account has a balance to close as of " + asOfDate);
        }

        JournalBuilder journal = JournalBuilder.forDate(asOfDate)
                .withIdempotencyKey(idempotencyKey)
                .describedAs("Year-end close as of " + asOfDate);

        BigDecimal netOfEverythingClosed = BigDecimal.ZERO;

        for (AccountWithBalance account : toClose) {
            // Base, not account-currency: milestone 15 lets a handful of
            // system-role accounts (revenue among them) hold entries in
            // more than one currency at once, and adding raw account-
            // currency amounts across those would add euros to dollars.
            // base_amount is comparable and addable regardless of what
            // currency each entry that fed it was actually posted in.
            BigDecimal balance = account.baseBalance();
            Money zeroingAmount = Money.of(balance.abs(), baseCurrency);
            if (balance.signum() > 0) {
                // A positive balance here is a contra account or a refund
                // that outran its revenue; either way, bringing it to zero
                // is a CREDIT (delta -amount) the same as any other decrease.
                journal.credit(account.id(), zeroingAmount);
            } else {
                journal.debit(account.id(), zeroingAmount);
            }
            netOfEverythingClosed = netOfEverythingClosed.add(balance);
        }

        // The single plug line. Every zeroing entry above sums, by
        // definition, to -netOfEverythingClosed; for the whole journal to
        // balance, Retained Earnings must take the exact opposite of that,
        // which is netOfEverythingClosed itself.
        if (netOfEverythingClosed.signum() > 0) {
            journal.debit(retainedEarnings.getId(), Money.of(netOfEverythingClosed, baseCurrency));
        } else if (netOfEverythingClosed.signum() < 0) {
            journal.credit(retainedEarnings.getId(), Money.of(netOfEverythingClosed.abs(), baseCurrency));
        }
        // A net of exactly zero needs no plug line at all, and is only
        // reachable here with at least two zeroing lines already in the
        // journal (a single non-zero account can never net to zero alone),
        // so JournalBuilder's own "at least two lines" invariant still holds.

        return postingService.post(journal.build());
    }
}
