package com.example.repository;

import com.example.entity.ActivityEntity;
import com.example.entity.HotelEntity;
import io.micronaut.data.model.vector.Vector;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Application-facing adapter for hybrid nearby searches.
 *
 * The actual Oracle SQL lives on the Micronaut Data repositories. This class
 * keeps the tool-facing API in terms of the existing entities while the
 * database query returns small read-only projections.
 */
@Singleton
public class SpatialSearchRepository {
    private final HotelRepository hotelRepository;
    private final ActivityRepository activityRepository;

    public SpatialSearchRepository(
        HotelRepository hotelRepository,
        ActivityRepository activityRepository
    ) {
        this.hotelRepository = hotelRepository;
        this.activityRepository = activityRepository;
    }

    public List<HotelEntity> searchHotelsByVectorNear(
        Vector embedding,
        double longitude,
        double latitude,
        double radiusKm,
        Double maxPrice
    ) {
        return hotelRepository.searchTop5ByEmbeddingNearLocation(
                embedding, longitude, latitude, radiusKm, maxPrice)
            .stream()
            .map(result -> new HotelEntity(
                result.id(),
                result.destinationId(),
                result.name(),
                result.pricePerNight(),
                result.description(),
                null,
                null
            ))
            .toList();
    }

    public List<ActivityEntity> searchActivitiesByVectorNear(
        Vector embedding,
        double longitude,
        double latitude,
        double radiusKm
    ) {
        return activityRepository.searchTop5ByEmbeddingNearLocation(
                embedding, longitude, latitude, radiusKm)
            .stream()
            .map(result -> new ActivityEntity(
                result.id(),
                result.destinationId(),
                result.name(),
                result.season(),
                result.description(),
                null,
                null
            ))
            .toList();
    }
}
