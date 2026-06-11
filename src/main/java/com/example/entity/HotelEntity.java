package com.example.entity;

import io.micronaut.core.annotation.Nullable;
import io.micronaut.data.annotation.GeneratedValue;
import io.micronaut.data.annotation.Id;
import io.micronaut.data.annotation.Index;
import io.micronaut.data.annotation.MappedEntity;
import io.micronaut.data.annotation.MappedProperty;
import io.micronaut.data.annotation.Srid;
import io.micronaut.data.annotation.VectorIndex;
import io.micronaut.data.annotation.VectorIndexType;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.vector.FloatVector;

@MappedEntity("hotels")
@Index(name = "idx_hotels_location", columns = "location")
public record HotelEntity(
    @Id
    @GeneratedValue
    Long id,

    @MappedProperty("destination_id")
    Long destinationId,

    String name,

    @MappedProperty("price_per_night")
    Double pricePerNight,

    String description,

    @Nullable
    @MappedProperty("description_embedding")
    @VectorIndex(
        name = "idx_hotels_description_embedding",
        vectorIndexType = VectorIndexType.IVF,
        distanceType = VectorIndexType.DistanceType.COSINE,
        accuracy = 90
    )
    FloatVector descriptionEmbedding,

    @Nullable
    @Srid(4326)
    Point location
) {
}
