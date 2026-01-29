package com.example.service;

import com.example.model.Activity;
import com.example.model.Destination;
import com.example.model.Hotel;
import com.example.repository.ActivityRepository;
import com.example.repository.DestinationRepository;
import com.example.repository.HotelRepository;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DataInitializer implements ApplicationEventListener<ServerStartupEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(DataInitializer.class);

    private final EmbeddingService embeddingService;
    private final DestinationRepository destinationRepository;
    private final HotelRepository hotelRepository;
    private final ActivityRepository activityRepository;

    public DataInitializer(
            EmbeddingService embeddingService,
            DestinationRepository destinationRepository,
            HotelRepository hotelRepository,
            ActivityRepository activityRepository) {
        this.embeddingService = embeddingService;
        this.destinationRepository = destinationRepository;
        this.hotelRepository = hotelRepository;
        this.activityRepository = activityRepository;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        LOG.info("Checking for missing embeddings...");

        try {
            int destinationCount = 0;
            int hotelCount = 0;
            int activityCount = 0;

            for (Destination destination : destinationRepository.findWithoutEmbedding()) {
                String text = destination.name() + " " + destination.region() + ". " + destination.description();
                float[] embedding = embeddingService.generateEmbedding(text);
                destinationRepository.updateEmbedding(destination.id(), embedding);
                destinationCount++;
            }

            for (Hotel hotel : hotelRepository.findWithoutEmbedding()) {
                String text = hotel.name() + " in " + hotel.destinationName() + ". " + hotel.description();
                float[] embedding = embeddingService.generateEmbedding(text);
                hotelRepository.updateEmbedding(hotel.id(), embedding);
                hotelCount++;
            }

            for (Activity activity : activityRepository.findWithoutEmbedding()) {
                String text = activity.name() + " in " + activity.destinationName() + ". " + activity.description();
                float[] embedding = embeddingService.generateEmbedding(text);
                activityRepository.updateEmbedding(activity.id(), embedding);
                activityCount++;
            }

            if (destinationCount + hotelCount + activityCount > 0) {
                LOG.info("Generated embeddings: {} destinations, {} hotels, {} activities",
                        destinationCount, hotelCount, activityCount);
            } else {
                LOG.info("All embeddings up to date");
            }
        } catch (Exception e) {
            LOG.error("Error generating embeddings", e);
        }
    }
}
