package com.ledgerflow.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

class SortWhitelistTest {

    private static final Set<String> ALLOWED = Set.of("name", "balance");
    private static final Sort FALLBACK = Sort.by("name").ascending();

    @Test
    void appliesTheFallbackSortWhenTheRequestDoesNotAskForOne() {
        Pageable result = SortWhitelist.apply(PageRequest.of(2, 25), ALLOWED, FALLBACK);

        assertThat(result.getSort()).isEqualTo(FALLBACK);
        assertThat(result.getPageNumber()).isEqualTo(2);
        assertThat(result.getPageSize()).isEqualTo(25);
    }

    @Test
    void passesThroughAnAllowedSort() {
        Pageable requested = PageRequest.of(0, 10, Sort.by("balance").descending());

        assertThat(SortWhitelist.apply(requested, ALLOWED, FALLBACK)).isEqualTo(requested);
    }

    @Test
    void rejectsAPropertyThatIsNotExposedForSorting() {
        Pageable requested = PageRequest.of(0, 10, Sort.by("passwordHash"));

        assertThatThrownBy(() -> SortWhitelist.apply(requested, ALLOWED, FALLBACK))
                .isInstanceOf(SortWhitelist.InvalidSortException.class)
                .hasMessageContaining("passwordHash")
                .hasMessageContaining("balance, name");
    }

    @Test
    void rejectsAMultiPropertySortWhereOnlyOnePropertyIsDisallowed() {
        Pageable requested = PageRequest.of(0, 10, Sort.by("name").and(Sort.by("version")));

        assertThatThrownBy(() -> SortWhitelist.apply(requested, ALLOWED, FALLBACK))
                .isInstanceOf(SortWhitelist.InvalidSortException.class)
                .hasMessageContaining("version");
    }
}
