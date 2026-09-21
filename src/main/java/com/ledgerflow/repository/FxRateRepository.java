package com.ledgerflow.repository;

import com.ledgerflow.domain.FxRate;
import java.time.LocalDate;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface FxRateRepository extends JpaRepository<FxRate, Long> {

    /** The rate this milestone's whole design exists to answer: the latest one on or before a given date. */
    Optional<FxRate> findFirstByCurrencyAndAsOfDateLessThanEqualOrderByAsOfDateDesc(String currency, LocalDate asOfDate);

    /** Recording today's rate a second time corrects the existing row rather than creating an ambiguous duplicate. */
    Optional<FxRate> findByCurrencyAndAsOfDate(String currency, LocalDate asOfDate);
}
