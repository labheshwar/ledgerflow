package com.ledgerflow.web;

import com.ledgerflow.service.ClosingService;
import com.ledgerflow.service.PeriodService;
import com.ledgerflow.web.dto.CloseYearRequest;
import com.ledgerflow.web.dto.PeriodRequest;
import com.ledgerflow.web.dto.PeriodResponse;
import com.ledgerflow.web.dto.TransactionResponse;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/periods")
public class PeriodController {

    private final PeriodService periodService;
    private final ClosingService closingService;

    public PeriodController(PeriodService periodService, ClosingService closingService) {
        this.periodService = periodService;
        this.closingService = closingService;
    }

    /** Not paged: an organization has a handful of periods, not pages of them. */
    @GetMapping
    public List<PeriodResponse> listPeriods() {
        return periodService.list().stream().map(PeriodResponse::from).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public PeriodResponse createPeriod(@Valid @RequestBody PeriodRequest request) {
        return PeriodResponse.from(periodService.create(request.startDate(), request.endDate()));
    }

    @PostMapping("/{id}/close")
    public PeriodResponse closePeriod(@PathVariable Long id) {
        return PeriodResponse.from(periodService.close(id));
    }

    @PostMapping("/{id}/reopen")
    public PeriodResponse reopenPeriod(@PathVariable Long id) {
        return PeriodResponse.from(periodService.reopen(id));
    }

    /**
     * Posts the year-end closing journal. Not scoped to a period id -- the
     * date it closes as of is what matters, and a business may want to see
     * the closing entry before deciding to lock the period it falls in.
     */
    @PostMapping("/close-year")
    @ResponseStatus(HttpStatus.CREATED)
    public TransactionResponse closeYear(@Valid @RequestBody CloseYearRequest request) {
        return TransactionResponse.from(closingService.closeFiscalYear(request.asOfDate()));
    }
}
