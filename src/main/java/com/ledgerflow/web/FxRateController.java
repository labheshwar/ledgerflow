package com.ledgerflow.web;

import com.ledgerflow.service.FxRateService;
import com.ledgerflow.web.dto.FxRateRequest;
import com.ledgerflow.web.dto.FxRateResponse;
import com.ledgerflow.web.dto.PagedResponse;
import jakarta.validation.Valid;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fx-rates")
public class FxRateController {

    private static final Set<String> SORTABLE = Set.of("currency", "asOfDate", "createdAt");

    private final FxRateService fxRateService;

    public FxRateController(FxRateService fxRateService) {
        this.fxRateService = fxRateService;
    }

    @GetMapping
    public PagedResponse<FxRateResponse> listFxRates(@ParameterObject @PageableDefault(size = 25) Pageable pageable) {
        Pageable sorted = SortWhitelist.apply(
                pageable, SORTABLE, Sort.by("currency").ascending().and(Sort.by("asOfDate").descending()));
        return PagedResponse.from(fxRateService.list(sorted), FxRateResponse::from);
    }

    /** Recording the same currency and date again corrects that rate rather than creating a duplicate. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public FxRateResponse recordFxRate(@Valid @RequestBody FxRateRequest request) {
        return FxRateResponse.from(fxRateService.record(request.currency(), request.rate(), request.asOfDate()));
    }
}
