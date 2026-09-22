package com.ledgerflow.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/** How overdue an open invoice or bill is, as of the date the projection was last rebuilt. */
public enum AgingBucket {
    CURRENT,
    DAYS_1_30,
    DAYS_31_60,
    DAYS_61_90,
    DAYS_OVER_90;

    public static AgingBucket forDueDate(LocalDate dueDate, LocalDate asOf) {
        long daysPastDue = ChronoUnit.DAYS.between(dueDate, asOf);
        if (daysPastDue <= 0) return CURRENT;
        if (daysPastDue <= 30) return DAYS_1_30;
        if (daysPastDue <= 60) return DAYS_31_60;
        if (daysPastDue <= 90) return DAYS_61_90;
        return DAYS_OVER_90;
    }
}
