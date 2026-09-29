package com.example.repository;

import com.example.entity.ActivityEntity;
import com.example.model.NearbyActivitySearchResult;
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

    @Query(value = """
        SELECT a.id,
               a.destination_id,
               a.name,
               a.season,
               a.description
        FROM activities a
        WHERE a.description_embedding IS NOT NULL
          AND a.location IS NOT NULL
          AND SDO_WITHIN_DISTANCE(
                a.location,
                MDSYS.SDO_GEOMETRY(
                    2001,
                    4326,
                    MDSYS.SDO_POINT_TYPE(:longitude, :latitude, NULL),
                    NULL,
                    NULL
                ),
                'distance=' || :radiusKm || ' unit=KM'
              ) = 'TRUE'
        ORDER BY VECTOR_DISTANCE(a.description_embedding, :embedding, COSINE)
        FETCH FIRST 5 ROWS ONLY
        """, nativeQuery = true)
    List<NearbyActivitySearchResult> searchTop5ByEmbeddingNearLocation(
        Vector embedding,
        double longitude,
        double latitude,
        double radiusKm
    );

    List<ActivityEntity> findByDescriptionEmbeddingIsNull();

    @Query(value = "UPDATE activities SET description_embedding = :embedding WHERE id = :id", nativeQuery = true)
    void updateDescriptionEmbedding(Long id, Vector embedding);
}
