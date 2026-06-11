package com.example.repository;

import com.example.entity.HotelEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface HotelRepository extends CrudRepository<HotelEntity, Long> {

    List<HotelEntity> findTop5ByDescriptionEmbeddingNear(Vector embedding, Double maxDistance);

    List<HotelEntity> findTop5ByDestinationIdAndDescriptionEmbeddingNear(Long destinationId, Vector embedding, Double maxDistance);

    List<HotelEntity> findTop5ByPricePerNightLessThanEqualsAndDescriptionEmbeddingNear(
        Double maxPrice,
        Vector embedding,
        Double maxDistance
    );

    List<HotelEntity> findTop5ByDestinationIdAndPricePerNightLessThanEqualsAndDescriptionEmbeddingNear(
        Long destinationId,
        Double maxPrice,
        Vector embedding,
        Double maxDistance
    );

    List<HotelEntity> findByDescriptionEmbeddingIsNull();

    @Query(value = "UPDATE hotels SET description_embedding = :embedding WHERE id = :id", nativeQuery = true)
    void updateDescriptionEmbedding(Long id, Vector embedding);
}
