package com.ledgerflow.service;

import java.math.BigDecimal;
import java.security.SecureRandom;
import org.springframework.stereotype.Component;

/**
 * Stands in for a real external source of truth (e.g. a bank settlement
 * file) that reconciliation would otherwise compare the ledger against.
 * Usually agrees with the ledger; occasionally drifts by a small amount so
 * the mismatch-detection path actually gets exercised. This is one of the
 * project's disclosed simplifications -- see the README limitations.
 */
@Component
public class SimulatedExternalStatementFeed {

    private static final int MISMATCH_ODDS = 5; // roughly 1 in 5 accounts drifts
    private static final int MAX_DRIFT_CENTS = 1000; // up to +/- 10.00

    private final SecureRandom random = new SecureRandom();

    /**
     * Takes the ledger balance rather than the account, because a balance is
     * no longer something an account row carries around with it.
     */
    public BigDecimal fetchExternalBalance(BigDecimal ledgerBalance) {
        if (random.nextInt(MISMATCH_ODDS) != 0) {
            return ledgerBalance;
        }
        int driftCents = random.nextInt(2 * MAX_DRIFT_CENTS + 1) - MAX_DRIFT_CENTS;
        BigDecimal drift = BigDecimal.valueOf(driftCents, 2);
        return ledgerBalance.add(drift);
    }
}
