package com.example.entity;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.Srid;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.vector.FloatVector;

@MappedEntity("hotels")
public record HotelEntity(
    @Id
    @GeneratedValue(GeneratedValue.Type.IDENTITY)
    Long id,

    Long destinationId,

    String name,

    Double pricePerNight,

    String description,

    // Exact search is intentional for the small demo catalog. At scale, add an
    // Oracle IVF or HNSW index through Flyway and use approximate top-K search.
    @Nullable
    FloatVector contentEmbedding,

    @Nullable
    @Srid(4326)
    Point location
) {
}
