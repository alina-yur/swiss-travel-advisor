package com.example.tools;

import com.example.model.Activity;
import com.example.model.Destination;
import com.example.model.Hotel;
import com.example.model.WishlistItem;
import com.example.repository.ActivityRepository;
import com.example.repository.DestinationRepository;
import com.example.repository.HotelRepository;
import com.example.service.EmbeddingService;
import com.example.service.WishlistService;
import dev.langchain4j.agent.tool.Tool;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

@Singleton
public class TravelTools {
    private static final Logger LOG = LoggerFactory.getLogger(TravelTools.class);

    private final EmbeddingService embeddingService;
    private final DestinationRepository destinationRepository;
    private final HotelRepository hotelRepository;
    private final ActivityRepository activityRepository;
    private final WishlistService wishlistService;

    public TravelTools(
        EmbeddingService embeddingService,
        DestinationRepository destinationRepository,
        HotelRepository hotelRepository,
        ActivityRepository activityRepository,
        WishlistService wishlistService
    ) {
        this.embeddingService = embeddingService;
        this.destinationRepository = destinationRepository;
        this.hotelRepository = hotelRepository;
        this.activityRepository = activityRepository;
        this.wishlistService = wishlistService;
    }

    @Tool("""
        Search for Swiss destinations matching the user's query.
        Use semantic search to find destinations based on user preferences like 'mountain views', 'lakeside', 'winter sports', etc.
        Returns a list of destinations with their names, regions, and descriptions.
        """)
    public List<Destination> searchDestinations(String query) {
        LOG.info("Searching destinations with query: {}", query);
        float[] queryVector = embeddingService.generateEmbedding(query);
        return destinationRepository.searchByVector(queryVector, 5);
    }

    @Tool("""
        Search for hotels matching the user's query.
        Optional parameters:
        - destinationId: Filter hotels by specific destination (use ID from searchDestinations)
        - maxPrice: Maximum price per night in CHF
        Use semantic search for queries like 'luxury hotel', 'budget friendly', 'spa hotel', etc.
        Returns a list of hotels with names, prices per night, and descriptions.
        """)
    public List<Hotel> searchHotels(String query, Long destinationId, Double maxPrice) {
        LOG.info("Searching hotels with query: {}, destinationId: {}, maxPrice: {}", query, destinationId, maxPrice);
        float[] queryVector = embeddingService.generateEmbedding(query);
        return hotelRepository.searchByVector(queryVector, destinationId, maxPrice, 5);
    }

    @Tool("""
        Search for activities matching the user's query.
        Optional parameter:
        - destinationId: Filter activities by specific destination (use ID from searchDestinations)
        Use semantic search for queries like 'outdoor adventure', 'scenic train', 'wine tasting', etc.
        Returns a list of activities with names, seasons, and descriptions.
        """)
    public List<Activity> searchActivities(String query, Long destinationId) {
        LOG.info("Searching activities with query: {}, destinationId: {}", query, destinationId);
        float[] queryVector = embeddingService.generateEmbedding(query);
        return activityRepository.searchByVector(queryVector, destinationId, 5);
    }

    @Tool("""
        Add an item to the user's wishlist.
        Parameters:
        - itemType: Must be one of 'destination', 'hotel', or 'activity'
        - itemId: The ID of the item to add (from search results)
        Use this when the user expresses interest in a destination, hotel, or activity.
        Returns a confirmation message.
        """)
    public String addToWishlist(String itemType, Long itemId) {
        LOG.info("Adding to wishlist: itemType={}, itemId={}", itemType, itemId);

        String name = "";
        String description = "";

        switch (itemType.toLowerCase()) {
            case "destination" -> {
                List<Destination> destinations = destinationRepository.findAll();
                Destination dest = destinations.stream()
                    .filter(d -> d.id().equals(itemId))
                    .findFirst()
                    .orElse(null);
                if (dest == null) {
                    return "Error: Destination not found with ID " + itemId;
                }
                name = dest.name() + " (" + dest.region() + ")";
                description = dest.description();
            }
            case "hotel" -> {
                Hotel hotel = hotelRepository.findById(itemId);
                if (hotel == null) {
                    return "Error: Hotel not found with ID " + itemId;
                }
                name = hotel.name() + " - CHF " + hotel.pricePerNight() + "/night";
                description = hotel.description();
            }
            case "activity" -> {
                Activity activity = activityRepository.findById(itemId);
                if (activity == null) {
                    return "Error: Activity not found with ID " + itemId;
                }
                name = activity.name() + " (" + activity.season() + ")";
                description = activity.description();
            }
            default -> {
                return "Error: Invalid item type. Must be 'destination', 'hotel', or 'activity'";
            }
        }

        WishlistItem item = new WishlistItem(itemType, itemId, name, description);
        wishlistService.addItem(item);
        return "Added to wishlist: " + name;
    }

    @Tool("""
        Get the user's current wishlist.
        Returns all destinations, hotels, and activities the user has added to their wishlist.
        Use this when the user asks to see their saved items or wishlist.
        """)
    public List<WishlistItem> getWishlist() {
        LOG.info("Retrieving wishlist");
        return wishlistService.getWishlist();
    }
}
