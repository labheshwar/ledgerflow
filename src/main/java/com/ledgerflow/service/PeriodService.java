package com.ledgerflow.service;

import com.ledgerflow.domain.AccountingPeriod;
import com.ledgerflow.domain.PeriodStatus;
import com.ledgerflow.exception.PeriodException;
import com.ledgerflow.repository.AccountingPeriodRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.NoSuchElementException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Locks the books.
 *
 * A period, once closed, refuses any transaction dated inside it -- enforced
 * by a database trigger (V15), which is what actually makes the rule true.
 * {@link #assertOpen} is the friendly half: PostingExecutor calls it before
 * ever reaching an INSERT, so a caller sees a clear 400 with the period's
 * dates in it, rather than a raw constraint violation.
 */
@Service
public class PeriodService {

    private final AccountingPeriodRepository periodRepository;

    public PeriodService(AccountingPeriodRepository periodRepository) {
        this.periodRepository = periodRepository;
    }

    @Transactional(readOnly = true)
    public List<AccountingPeriod> list() {
        return periodRepository.findAllByOrderByStartDateAsc();
    }

    @Transactional
    public AccountingPeriod create(LocalDate startDate, LocalDate endDate) {
        if (startDate == null || endDate == null) {
            throw new PeriodException("INVALID_PERIOD", "A period needs both a start and an end date");
        }
        if (endDate.isBefore(startDate)) {
            throw new PeriodException("INVALID_PERIOD", "A period's end date cannot be before its start date");
        }

        AccountingPeriod period = new AccountingPeriod();
        period.setOrgId(TenantContext.require());
        period.setStartDate(startDate);
        period.setEndDate(endDate);

        try {
            return periodRepository.saveAndFlush(period);
        } catch (DataIntegrityViolationException e) {
            // Flushed inside the try so the constraint fires here, where it
            // can be attributed, rather than silently at commit.
            throw new PeriodException(
                    "OVERLAPPING_PERIOD",
                    "%s to %s overlaps a period that already exists".formatted(startDate, endDate));
        }
    }

    @Transactional
    public AccountingPeriod close(Long periodId) {
        AccountingPeriod period = require(periodId);
        if (period.getStatus() == PeriodStatus.CLOSED) {
            throw new PeriodException(
                    "ALREADY_CLOSED", "The period %s to %s is already closed".formatted(period.getStartDate(), period.getEndDate()));
        }
        period.setStatus(PeriodStatus.CLOSED);
        period.setClosedAt(OffsetDateTime.now());
        return periodRepository.save(period);
    }

    /**
     * Undoes a close. There is no separate audit of who reopened what here
     * beyond the ordinary audit log entry AuditService would record for any
     * business mutation -- a real deployment would want this gated tightly
     * (it is already ADMIN-only) since reopening is what lets a "closed"
     * period be posted into again.
     */
    @Transactional
    public AccountingPeriod reopen(Long periodId) {
        AccountingPeriod period = require(periodId);
        if (period.getStatus() == PeriodStatus.OPEN) {
            throw new PeriodException(
                    "NOT_CLOSED", "The period %s to %s is not closed".formatted(period.getStartDate(), period.getEndDate()));
        }
        period.setStatus(PeriodStatus.OPEN);
        period.setClosedAt(null);
        return periodRepository.save(period);
    }

    /**
     * @throws PeriodException if {@code txnDate} falls inside a closed
     *         period for the current organization.
     */
    @Transactional(readOnly = true)
    public void assertOpen(LocalDate txnDate) {
        if (periodRepository.existsCoveringDateWithStatus(txnDate, PeriodStatus.CLOSED)) {
            throw new PeriodException(
                    "PERIOD_CLOSED", "The accounting period covering %s is closed".formatted(txnDate));
        }
    }

    private AccountingPeriod require(Long periodId) {
        return periodRepository
                .findById(periodId)
                .orElseThrow(() -> new NoSuchElementException("No accounting period with id " + periodId));
    }
}
