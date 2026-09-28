package com.example.model;

import io.micronaut.core.annotation.Introspected;
import io.micronaut.serde.annotation.Serdeable;

/**
 * Read-only projection for the destination hybrid search.
 *
 * @param vectorDistance raw cosine distance; lower values are closer matches
 */
@Introspected
@Serdeable
public record DestinationSearchResult(
    Long id,
    String name,
    String region,
    String description,
    double vectorDistance
) {
}
