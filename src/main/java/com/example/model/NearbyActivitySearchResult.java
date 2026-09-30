package com.example.model;

import io.micronaut.core.annotation.Introspected;

/**
 * Read-only projection for the Oracle spatial plus vector activity search.
 */
@Introspected
public record NearbyActivitySearchResult(
    Long id,
    Long destinationId,
    String name,
    String season,
    String description,
    double vectorDistance
) {
}
