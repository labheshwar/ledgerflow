package com.ledgerflow.web;

import com.ledgerflow.service.TaxRateService;
import com.ledgerflow.web.dto.PagedResponse;
import com.ledgerflow.web.dto.TaxRateRequest;
import com.ledgerflow.web.dto.TaxRateResponse;
import jakarta.validation.Valid;
import java.util.Set;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/tax-rates")
public class TaxRateController {

    private static final Set<String> SORTABLE = Set.of("name", "rate", "createdAt", "updatedAt");

    private final TaxRateService taxRateService;

    public TaxRateController(TaxRateService taxRateService) {
        this.taxRateService = taxRateService;
    }

    @GetMapping
    public PagedResponse<TaxRateResponse> listTaxRates(
            @RequestParam(required = false) String q,
            @RequestParam(name = "includeArchived", defaultValue = "false") boolean includeArchived,
            @ParameterObject @PageableDefault(size = 25) Pageable pageable) {

        Pageable sorted = SortWhitelist.apply(pageable, SORTABLE, Sort.by("name").ascending());
        return PagedResponse.from(taxRateService.search(q, includeArchived, sorted), TaxRateResponse::from);
    }

    @GetMapping("/{id}")
    public TaxRateResponse getTaxRate(@PathVariable Long id) {
        return TaxRateResponse.from(taxRateService.get(id));
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TaxRateResponse createTaxRate(@Valid @RequestBody TaxRateRequest request) {
        return TaxRateResponse.from(taxRateService.create(request.toDraft()));
    }

    @PutMapping("/{id}")
    public TaxRateResponse updateTaxRate(@PathVariable Long id, @Valid @RequestBody TaxRateRequest request) {
        return TaxRateResponse.from(taxRateService.update(id, request.toDraft()));
    }

    @PostMapping("/{id}/archive")
    public TaxRateResponse archiveTaxRate(@PathVariable Long id) {
        return TaxRateResponse.from(taxRateService.archive(id));
    }

    @PostMapping("/{id}/restore")
    public TaxRateResponse restoreTaxRate(@PathVariable Long id) {
        return TaxRateResponse.from(taxRateService.restore(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteTaxRate(@PathVariable Long id) {
        taxRateService.delete(id);
    }
}
