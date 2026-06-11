package com.example.service;

import com.example.repository.EmbeddingBackfillRepository;
import com.example.repository.EmbeddingBackfillRepository.ActivityEmbeddingSeed;
import com.example.repository.EmbeddingBackfillRepository.DestinationEmbeddingSeed;
import com.example.repository.EmbeddingBackfillRepository.HotelEmbeddingSeed;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.runtime.server.event.ServerStartupEvent;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

@Singleton
public class DataInitializer implements ApplicationEventListener<ServerStartupEvent> {
    private static final Logger LOG = LoggerFactory.getLogger(DataInitializer.class);

    private final EmbeddingService embeddingService;
    private final EmbeddingBackfillRepository embeddingBackfillRepository;

    public DataInitializer(
            EmbeddingService embeddingService,
            EmbeddingBackfillRepository embeddingBackfillRepository) {
        this.embeddingService = embeddingService;
        this.embeddingBackfillRepository = embeddingBackfillRepository;
    }

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        LOG.info("Checking for missing embeddings...");

        try {
            int destinationCount = 0;
            int hotelCount = 0;
            int activityCount = 0;

            for (DestinationEmbeddingSeed destination : embeddingBackfillRepository.findDestinationsWithoutEmbedding()) {
                String text = destination.name() + " " + destination.region() + ". " + destination.description();
                float[] embedding = embeddingService.generateEmbedding(text);
                embeddingBackfillRepository.updateDestinationEmbedding(destination.id(), embedding);
                destinationCount++;
            }

            for (HotelEmbeddingSeed hotel : embeddingBackfillRepository.findHotelsWithoutEmbedding()) {
                String text = hotel.name() + " in " + hotel.destinationName() + ". " + hotel.description();
                float[] embedding = embeddingService.generateEmbedding(text);
                embeddingBackfillRepository.updateHotelEmbedding(hotel.id(), embedding);
                hotelCount++;
            }

            for (ActivityEmbeddingSeed activity : embeddingBackfillRepository.findActivitiesWithoutEmbedding()) {
                String text = activity.name() + " in " + activity.destinationName() + " (" + activity.season() + "). " + activity.description();
                float[] embedding = embeddingService.generateEmbedding(text);
                embeddingBackfillRepository.updateActivityEmbedding(activity.id(), embedding);
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
