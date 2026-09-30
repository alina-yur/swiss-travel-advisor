package com.example.repository;

import com.example.entity.HotelEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.SearchResults;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface HotelRepository extends CrudRepository<HotelEntity, Long> {

    List<HotelEntity> findTop5ByContentEmbeddingNear(Vector embedding, Double maxDistance);

    List<HotelEntity> findTop5ByDestinationIdAndContentEmbeddingNear(Long destinationId, Vector embedding, Double maxDistance);

    List<HotelEntity> findTop5ByPricePerNightLessThanEqualsAndContentEmbeddingNear(
        Double maxPrice,
        Vector embedding,
        Double maxDistance
    );

    List<HotelEntity> findTop5ByDestinationIdAndPricePerNightLessThanEqualsAndContentEmbeddingNear(
        Long destinationId,
        Double maxPrice,
        Vector embedding,
        Double maxDistance
    );

    SearchResults<HotelEntity> searchTop5ByContentEmbeddingNearAndLocationNearAndPricePerNightLessThanEquals(
        Vector embedding,
        Score maxDistance,
        Point location,
        double radiusMeters,
        Double maxPrice
    );

    List<HotelEntity> findByContentEmbeddingIsNull();

    @Query(value = "UPDATE hotels SET content_embedding = :embedding WHERE id = :id", nativeQuery = true)
    void updateContentEmbedding(Long id, Vector embedding);
}
