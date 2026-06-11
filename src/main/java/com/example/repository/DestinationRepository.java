package com.example.repository;

import com.example.entity.DestinationEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface DestinationRepository extends CrudRepository<DestinationEntity, Long> {

    double MAX_COSINE_DISTANCE = 2.0;

    List<DestinationEntity> findTop5ByDescriptionEmbeddingNear(Vector embedding, Double maxDistance);

    List<DestinationEntity> findTop5ByLocationNear(Point point, double distance);

    List<DestinationEntity> findByDescriptionEmbeddingIsNull();

    Optional<DestinationEntity> findByNameEqualsIgnoreCase(String name);

    @Query(value = "UPDATE destinations SET description_embedding = :embedding WHERE id = :id", nativeQuery = true)
    void updateDescriptionEmbedding(Long id, Vector embedding);
}
