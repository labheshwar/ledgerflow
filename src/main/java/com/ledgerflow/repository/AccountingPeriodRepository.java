package com.ledgerflow.repository;

import com.ledgerflow.domain.AccountingPeriod;
import com.ledgerflow.domain.PeriodStatus;
import java.time.LocalDate;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

@Transactional(readOnly = true)
public interface AccountingPeriodRepository extends JpaRepository<AccountingPeriod, Long> {

    List<AccountingPeriod> findAllByOrderByStartDateAsc();

    /**
     * The app-level half of "closed means closed" -- checked before ever
     * reaching an INSERT, so a caller gets a clear 400 instead of a raw
     * constraint violation. The database trigger from V15 is the actual
     * defense; this exists purely to answer faster and more legibly.
     */
    @Query(
            """
            SELECT CASE WHEN COUNT(p) > 0 THEN true ELSE false END
            FROM AccountingPeriod p
            WHERE p.status = :status
              AND p.startDate <= :date
              AND p.endDate >= :date
            """)
    boolean existsCoveringDateWithStatus(@Param("date") LocalDate date, @Param("status") PeriodStatus status);
}
