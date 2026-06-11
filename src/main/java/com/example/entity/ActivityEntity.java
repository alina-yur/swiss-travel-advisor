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

@MappedEntity("activities")
@Index(name = "idx_activities_location", columns = "location")
public record ActivityEntity(
    @Id
    @GeneratedValue
    Long id,

    @MappedProperty("destination_id")
    Long destinationId,

    String name,

    String season,

    String description,

    @Nullable
    @MappedProperty("description_embedding")
    @VectorIndex(
        name = "idx_activities_description_embedding",
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
