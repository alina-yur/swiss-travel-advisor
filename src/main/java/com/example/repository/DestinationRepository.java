package com.example.repository;

import com.example.entity.DestinationEntity;
import com.example.model.DestinationSearchResult;
import io.micronaut.data.annotation.Query;
import io.micronaut.data.jdbc.annotation.JdbcRepository;
import io.micronaut.data.model.query.builder.sql.Dialect;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.repository.CrudRepository;

import java.util.List;
import java.util.Optional;

@JdbcRepository(dialect = Dialect.ORACLE)
public interface DestinationRepository extends CrudRepository<DestinationEntity, Long> {

    List<DestinationEntity> findTop5ByContentEmbeddingNear(Vector embedding, Double maxDistance);

    @Query(value = """
        SELECT id,
               name,
               region,
               description,
               vector_distance
        FROM (
            SELECT d.id,
                   d.name,
                   d.region,
                   d.description,
                   VECTOR_DISTANCE(d.content_embedding, :embedding, COSINE) AS vector_distance
            FROM destinations d
            WHERE d.content_embedding IS NOT NULL
              AND d.location IS NOT NULL
              AND SDO_WITHIN_DISTANCE(
                    d.location,
                    MDSYS.SDO_GEOMETRY(
                        2001,
                        4326,
                        MDSYS.SDO_POINT_TYPE(:longitude, :latitude, NULL),
                        NULL,
                        NULL
                    ),
                    'distance=' || :radiusKm || ' unit=KM'
                  ) = 'TRUE'
        )
        ORDER BY vector_distance
        FETCH FIRST 5 ROWS ONLY
        """, nativeQuery = true)
    List<DestinationSearchResult> searchTop5ByEmbeddingNearLocation(
        Vector embedding,
        double longitude,
        double latitude,
        double radiusKm
    );

    List<DestinationEntity> findByContentEmbeddingIsNull();

    Optional<DestinationEntity> findByNameEqualsIgnoreCase(String name);

    @Query(value = "UPDATE destinations SET content_embedding = :embedding WHERE id = :id", nativeQuery = true)
    void updateContentEmbedding(Long id, Vector embedding);
}
