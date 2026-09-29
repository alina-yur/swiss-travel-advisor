package com.example.model;

import io.micronaut.core.annotation.Introspected;

/**
 * Read-only projection for the Oracle spatial plus vector hotel search.
 */
@Introspected
public record NearbyHotelSearchResult(
    Long id,
    Long destinationId,
    String name,
    Double pricePerNight,
    String description
) {
}
