package com.example.repository;

import com.example.entity.ActivityEntity;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface ActivityRepository extends CrudRepository<ActivityEntity, Long> {

    List<ActivityEntity> findTop5ByDescriptionEmbeddingNear(Vector embedding, Double maxDistance);

    List<ActivityEntity> findTop5ByDestinationIdAndDescriptionEmbeddingNear(Long destinationId, Vector embedding, Double maxDistance);

    List<ActivityEntity> findByDescriptionEmbeddingIsNull();

    @Query(value = "UPDATE activities SET description_embedding = :embedding WHERE id = :id", nativeQuery = true)
    void updateDescriptionEmbedding(Long id, Vector embedding);
}
