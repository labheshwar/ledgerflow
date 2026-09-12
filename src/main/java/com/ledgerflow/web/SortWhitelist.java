package com.ledgerflow.web;

import java.util.Set;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

/**
 * Spring's Pageable resolver will happily bind ?sort=passwordHash, turning a
 * query parameter into a path into any mapped property -- including ones no
 * endpoint means to expose, and ones with no index behind them. Each endpoint
 * declares what it can be sorted by and anything else is rejected.
 */
public final class SortWhitelist {

    private SortWhitelist() {}

    public static Pageable apply(Pageable pageable, Set<String> allowed, Sort fallback) {
        if (pageable.getSort().isUnsorted()) {
            return PageRequest.of(pageable.getPageNumber(), pageable.getPageSize(), fallback);
        }

        for (Sort.Order order : pageable.getSort()) {
            if (!allowed.contains(order.getProperty())) {
                throw new InvalidSortException(order.getProperty(), allowed);
            }
        }

        return pageable;
    }

    public static class InvalidSortException extends RuntimeException {
        public InvalidSortException(String property, Set<String> allowed) {
            super("Cannot sort by '" + property + "'. Allowed: " + String.join(", ", allowed.stream().sorted().toList()));
        }
    }
}
