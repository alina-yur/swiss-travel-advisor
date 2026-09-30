package com.example.repository;

import com.example.entity.ActivityEntity;
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
public interface ActivityRepository extends CrudRepository<ActivityEntity, Long> {

    List<ActivityEntity> findTop5ByContentEmbeddingNear(Vector embedding, Double maxDistance);

    List<ActivityEntity> findTop5ByDestinationIdAndContentEmbeddingNear(Long destinationId, Vector embedding, Double maxDistance);

    SearchResults<ActivityEntity> searchTop5ByContentEmbeddingNearAndLocationNear(
        Vector embedding,
        Score maxDistance,
        Point location,
        double radiusMeters
    );

    List<ActivityEntity> findByContentEmbeddingIsNull();

    @Query(value = "UPDATE activities SET content_embedding = :embedding WHERE id = :id", nativeQuery = true)
    void updateContentEmbedding(Long id, Vector embedding);
}
