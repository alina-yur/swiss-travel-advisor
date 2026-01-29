package com.example.tools;

import com.example.model.Activity;
import com.example.model.Destination;
import com.example.model.Hotel;
import com.example.model.WishlistItem;
import com.example.repository.ActivityRepository;
import com.example.repository.DestinationRepository;
import com.example.repository.HotelRepository;
import com.example.repository.WishlistRepository;
import com.example.service.EmbeddingService;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Singleton;

import java.util.List;

@Singleton
public class TravelTools {

    private final EmbeddingService embeddingService;
    private final DestinationRepository destinationRepository;
    private final HotelRepository hotelRepository;
    private final ActivityRepository activityRepository;
    private final WishlistRepository wishlistRepository;

    public TravelTools(
        EmbeddingService embeddingService,
        DestinationRepository destinationRepository,
        HotelRepository hotelRepository,
        ActivityRepository activityRepository,
        WishlistRepository wishlistRepository
    ) {
        this.embeddingService = embeddingService;
        this.destinationRepository = destinationRepository;
        this.hotelRepository = hotelRepository;
        this.activityRepository = activityRepository;
        this.wishlistRepository = wishlistRepository;
    }

    @Tool("Search for Swiss destinations by preference (e.g., 'mountain views', 'lakeside', 'winter sports').")
    public String searchDestinations(String query) {
        float[] queryVector = embeddingService.generateEmbedding(query);
        List<Destination> results = destinationRepository.searchByVector(queryVector, 5);
        if (results.isEmpty()) {
            return "No destinations found matching: " + query;
        }
        StringBuilder sb = new StringBuilder("Found destinations:\n");
        for (Destination d : results) {
            sb.append(String.format("- %s (ID:%d, %s): %s\n", d.name(), d.id(), d.region(), d.description()));
        }
        return sb.toString();
    }

    @Tool("Search for hotels. Optional filters: destinationId, maxPrice (CHF/night).")
    public String searchHotels(String query, Long destinationId, Double maxPrice) {
        float[] queryVector = embeddingService.generateEmbedding(query);
        List<Hotel> results = hotelRepository.searchByVector(queryVector, destinationId, maxPrice, 5);
        if (results.isEmpty()) {
            return "No hotels found matching: " + query;
        }
        StringBuilder sb = new StringBuilder("Found hotels:\n");
        for (Hotel h : results) {
            sb.append(String.format("- %s (ID:%d, CHF %.0f/night): %s\n", h.name(), h.id(), h.pricePerNight(), h.description()));
        }
        return sb.toString();
    }

    @Tool("Search for activities. Optional filter: destinationId.")
    public String searchActivities(String query, Long destinationId) {
        float[] queryVector = embeddingService.generateEmbedding(query);
        List<Activity> results = activityRepository.searchByVector(queryVector, destinationId, 5);
        if (results.isEmpty()) {
            return "No activities found matching: " + query;
        }
        StringBuilder sb = new StringBuilder("Found activities:\n");
        for (Activity a : results) {
            sb.append(String.format("- %s (ID:%d, %s): %s\n", a.name(), a.id(), a.season(), a.description()));
        }
        return sb.toString();
    }

    @Tool("Add an item to the wishlist. itemType: 'destination', 'hotel', or 'activity'. itemId: from search results.")
    public String addToWishlist(String itemType, Long itemId) {
        String type = itemType.toLowerCase();
        String name = switch (type) {
            case "destination" -> {
                Destination d = destinationRepository.findById(itemId);
                yield d != null ? d.name() : null;
            }
            case "hotel" -> {
                Hotel h = hotelRepository.findById(itemId);
                yield h != null ? h.name() : null;
            }
            case "activity" -> {
                Activity a = activityRepository.findById(itemId);
                yield a != null ? a.name() : null;
            }
            default -> null;
        };
        if (name == null) {
            return "Error: " + itemType + " with ID " + itemId + " not found.";
        }
        wishlistRepository.save(new WishlistItem(type, itemId));
        return "Added to wishlist: " + name;
    }

    @Tool("Get the user's wishlist with all saved destinations, hotels, and activities.")
    public String getWishlist() {
        List<WishlistItem> items = wishlistRepository.findAll();
        if (items.isEmpty()) {
            return "Your wishlist is empty.";
        }
        StringBuilder sb = new StringBuilder("Your wishlist:\n");
        for (WishlistItem item : items) {
            String detail = switch (item.itemType()) {
                case "destination" -> {
                    Destination d = destinationRepository.findById(item.itemId());
                    yield d != null ? d.name() + " (" + d.region() + ")" : "Unknown destination";
                }
                case "hotel" -> {
                    Hotel h = hotelRepository.findById(item.itemId());
                    yield h != null ? h.name() + " - CHF " + h.pricePerNight() + "/night" : "Unknown hotel";
                }
                case "activity" -> {
                    Activity a = activityRepository.findById(item.itemId());
                    yield a != null ? a.name() + " (" + a.season() + ")" : "Unknown activity";
                }
                default -> "Unknown item";
            };
            sb.append("- ").append(detail).append("\n");
        }
        return sb.toString();
    }
}
