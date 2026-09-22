package com.ledgerflow.service;

import com.ledgerflow.domain.FxRate;
import com.ledgerflow.exception.FxRateException;
import com.ledgerflow.repository.FxRateRepository;
import com.ledgerflow.tenancy.TenantContext;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Locale;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Exchange rates, always expressed as "how many units of the organization's
 * own base currency equal one unit of this currency" -- the direction
 * {@link com.ledgerflow.money.Money#convertedTo} already expects.
 *
 * There is deliberately no rate for the base currency itself, and asking
 * for one always answers 1 without a database read -- the base currency's
 * own rate to itself is a fact about arithmetic, not something anyone
 * records.
 */
@Service
public class FxRateService {

    private final FxRateRepository fxRateRepository;
    private final OrganizationService organizationService;

    public FxRateService(FxRateRepository fxRateRepository, OrganizationService organizationService) {
        this.fxRateRepository = fxRateRepository;
        this.organizationService = organizationService;
    }

    @Transactional(readOnly = true)
    public Page<FxRate> list(Pageable pageable) {
        return fxRateRepository.findAll(pageable);
    }

    /**
     * The rate to convert an amount in {@code currency} into the
     * organization's own base currency, as of the latest rate on or before
     * {@code asOfDate}.
     *
     * @throws FxRateException with code MISSING_FX_RATE if the currency is
     *         not the base currency and no rate on or before that date has
     *         ever been recorded
     */
    @Transactional(readOnly = true)
    public BigDecimal rateAsOf(String currency, LocalDate asOfDate) {
        return rateAsOfIfKnown(currency, asOfDate)
                .orElseThrow(() -> new FxRateException(
                        "MISSING_FX_RATE",
                        "No exchange rate for %s on or before %s -- record one first"
                                .formatted(currency.toUpperCase(Locale.ROOT), asOfDate)));
    }

    /**
     * The same lookup as {@link #rateAsOf}, but for a caller -- a report
     * aggregating many documents at once -- for which "nobody has recorded
     * this currency's rate yet" is a normal, expected outcome rather than a
     * failure. Returning empty rather than throwing matters beyond
     * ergonomics: this method is itself transactional, and an exception
     * thrown out of it marks whatever transaction is already under way
     * rollback-only before a caller's own try/catch ever gets a chance to
     * decide the failure is recoverable.
     */
    @Transactional(readOnly = true)
    public Optional<BigDecimal> rateAsOfIfKnown(String currency, LocalDate asOfDate) {
        String normalized = currency.toUpperCase(Locale.ROOT);
        if (normalized.equals(organizationService.baseCurrency())) {
            return Optional.of(BigDecimal.ONE);
        }
        return fxRateRepository
                .findFirstByCurrencyAndAsOfDateLessThanEqualOrderByAsOfDateDesc(normalized, asOfDate)
                .map(FxRate::getRate);
    }

    /** Recording the same currency and date again corrects that rate rather than creating an ambiguous duplicate. */
    @Transactional
    public FxRate record(String currency, BigDecimal rate, LocalDate asOfDate) {
        Long orgId = TenantContext.require();
        String normalized = requireCurrency(currency);
        BigDecimal validRate = requireRate(rate);
        LocalDate validDate = requireDate(asOfDate);

        FxRate fxRate = fxRateRepository.findByCurrencyAndAsOfDate(normalized, validDate).orElseGet(() -> {
            FxRate created = new FxRate();
            created.setOrgId(orgId);
            created.setCurrency(normalized);
            created.setAsOfDate(validDate);
            return created;
        });
        fxRate.setRate(validRate);
        return fxRateRepository.save(fxRate);
    }

    private String requireCurrency(String currency) {
        if (currency == null || currency.isBlank()) {
            throw new FxRateException("INVALID_CURRENCY", "An exchange rate needs a currency");
        }
        String normalized = currency.trim().toUpperCase(Locale.ROOT);
        try {
            Currency.getInstance(normalized);
        } catch (IllegalArgumentException e) {
            throw new FxRateException("INVALID_CURRENCY", "Not a known ISO 4217 currency code: " + normalized);
        }
        if (normalized.equals(organizationService.baseCurrency())) {
            throw new FxRateException(
                    "CANNOT_RATE_BASE_CURRENCY",
                    "%s is this organization's own base currency; its rate to itself is always 1".formatted(normalized));
        }
        return normalized;
    }

    private BigDecimal requireRate(BigDecimal rate) {
        if (rate == null || rate.signum() <= 0) {
            throw new FxRateException("INVALID_RATE", "An exchange rate must be greater than zero");
        }
        return rate;
    }

    private LocalDate requireDate(LocalDate asOfDate) {
        if (asOfDate == null) {
            throw new FxRateException("INVALID_DATE", "An exchange rate needs a date it applies as of");
        }
        return asOfDate;
    }
}
