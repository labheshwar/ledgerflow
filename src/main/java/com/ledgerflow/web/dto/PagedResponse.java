package com.ledgerflow.web.dto;

import java.util.List;
import java.util.function.Function;
import org.springframework.data.domain.Page;

/**
 * Explicit page envelope rather than serializing Spring's PageImpl, whose
 * JSON shape is an implementation detail Spring itself warns about relying
 * on. Clients get a stable contract they can generate types from.
 */
public record PagedResponse<T>(List<T> content, int page, int size, long totalElements, int totalPages) {

    public static <E, T> PagedResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PagedResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages());
    }

    public static <T> PagedResponse<T> of(List<T> content, Page<?> page) {
        return new PagedResponse<>(
                content, page.getNumber(), page.getSize(), page.getTotalElements(), page.getTotalPages());
    }
}
