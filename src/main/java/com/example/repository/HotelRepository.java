package com.example.repository;

import com.example.entity.HotelEntity;
import com.example.model.NearbyHotelSearchResult;
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

    @Query(value = """
        SELECT h.id,
               h.destination_id,
               h.name,
               h.price_per_night,
               h.description
        FROM hotels h
        WHERE h.description_embedding IS NOT NULL
          AND h.location IS NOT NULL
          AND (:maxPrice IS NULL OR h.price_per_night <= :maxPrice)
          AND SDO_WITHIN_DISTANCE(
                h.location,
                MDSYS.SDO_GEOMETRY(
                    2001,
                    4326,
                    MDSYS.SDO_POINT_TYPE(:longitude, :latitude, NULL),
                    NULL,
                    NULL
                ),
                'distance=' || :radiusKm || ' unit=KM'
              ) = 'TRUE'
        ORDER BY VECTOR_DISTANCE(h.description_embedding, :embedding, COSINE)
        FETCH FIRST 5 ROWS ONLY
        """, nativeQuery = true)
    List<NearbyHotelSearchResult> searchTop5ByEmbeddingNearLocation(
        Vector embedding,
        double longitude,
        double latitude,
        double radiusKm,
        Double maxPrice
    );

    List<HotelEntity> findByDescriptionEmbeddingIsNull();

    @Query(value = "UPDATE hotels SET description_embedding = :embedding WHERE id = :id", nativeQuery = true)
    void updateDescriptionEmbedding(Long id, Vector embedding);
}
