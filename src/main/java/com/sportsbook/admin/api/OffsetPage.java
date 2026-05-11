package com.sportsbook.admin.api;

import java.util.List;
import org.springframework.data.domain.Page;

/**
 * Offset-paginated response wrapper (ADR-0004 — admin lists use offset pagination, not cursor).
 * shared-protocol does not yet ship a page wrapper, so admin-api defines its own.
 */
public record OffsetPage<T>(List<T> items, int page, int size, long totalElements, int totalPages) {

  public static <T> OffsetPage<T> from(Page<?> source, List<T> items) {
    return new OffsetPage<>(
        items,
        source.getNumber(),
        source.getSize(),
        source.getTotalElements(),
        source.getTotalPages());
  }
}
