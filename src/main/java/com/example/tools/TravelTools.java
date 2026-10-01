package com.example.tools;

import com.example.entity.ActivityEntity;
import com.example.entity.DestinationEntity;
import com.example.entity.HotelEntity;
import com.example.model.WishlistItem;
import com.example.model.DestinationSearchResult;
import com.example.observability.AiObservability;
import com.example.repository.ActivityRepository;
import com.example.repository.DestinationRepository;
import com.example.repository.HotelRepository;
import com.example.repository.WishlistRepository;
import com.example.service.EmbeddingService;
import dev.langchain4j.agent.tool.Tool;
import dev.langchain4j.agent.tool.ToolMemoryId;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.vector.FloatVector;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.data.model.vector.search.Score;
import io.micronaut.data.model.vector.search.SearchResult;
import jakarta.inject.Singleton;

import java.util.List;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

@Singleton
public class TravelTools {
    private static final double MAX_COSINE_DISTANCE = 2.0;
    private static final double DEFAULT_DESTINATION_RADIUS_KM = 50.0;
    private static final double DEFAULT_HOTEL_RADIUS_KM = 15.0;
    private static final double DEFAULT_ACTIVITY_RADIUS_KM = 40.0;
    private static final double UNBOUNDED_HOTEL_PRICE = 99_999_999.99;

    private final EmbeddingService embeddingService;
    private final DestinationRepository destinationRepository;
    private final HotelRepository hotelRepository;
    private final ActivityRepository activityRepository;
    private final WishlistRepository wishlistRepository;
    private final AiObservability observability;

    public TravelTools(
        EmbeddingService embeddingService,
        DestinationRepository destinationRepository,
        HotelRepository hotelRepository,
        ActivityRepository activityRepository,
        WishlistRepository wishlistRepository,
        AiObservability observability
    ) {
        this.embeddingService = embeddingService;
        this.destinationRepository = destinationRepository;
        this.hotelRepository = hotelRepository;
        this.activityRepository = activityRepository;
        this.wishlistRepository = wishlistRepository;
        this.observability = observability;
    }

    @Tool("Search for Swiss destinations by preference when there is no location constraint. For 'in', 'near', 'around', or 'within km of' requests, use searchNearbyDestinations instead.")
    public String searchDestinations(String query) {
        return observability.traceTool("searchDestinations", parameters("query", query),
                () -> doSearchDestinations(query));
    }

    private String doSearchDestinations(String query) {
        Vector queryVector = embedding(query, "destinations");
        List<DestinationEntity> results = observability.traceRetriever(
                "Oracle destination vector search",
                parameters("entity.type", "destination", "max_distance", MAX_COSINE_DISTANCE),
                () -> destinationRepository.findTop5ByContentEmbeddingNear(queryVector, MAX_COSINE_DISTANCE),
                destination -> destinationDocument(destination, cosineDistance(queryVector, destination.contentEmbedding())));
        if (results.isEmpty()) {
            return "No destinations found matching: " + query;
        }
        StringBuilder sb = new StringBuilder("Found destinations:\n");
        for (DestinationEntity d : results) {
            sb.append(String.format("- %s (ID:%d, %s): %s\n", d.name(), d.id(), d.region(), d.description()));
        }
        return sb.toString();
    }

    @Tool("Search for Swiss destinations by preference near a location anchor. Supported anchors: Zermatt, Interlaken, Lucerne, Lausanne, St. Moritz, Lugano, Zurich. radiusKm defaults to 50.")
    public String searchNearbyDestinations(String query, String nearDestinationName, Double radiusKm) {
        return observability.traceTool("searchNearbyDestinations", parameters(
                        "query", query, "nearDestinationName", nearDestinationName, "radiusKm", radiusKm),
                () -> doSearchNearbyDestinations(query, nearDestinationName, radiusKm));
    }

    private String doSearchNearbyDestinations(String query, String nearDestinationName, Double radiusKm) {
        Optional<Point> location = locationForDestination(nearDestinationName);
        if (location.isEmpty()) {
            return unsupportedLocation("nearby search", nearDestinationName);
        }

        double radius = radiusOrDefault(radiusKm, DEFAULT_DESTINATION_RADIUS_KM);
        Point point = location.get();
        Vector queryVector = embedding(query, "destinations");
        List<DestinationSearchResult> results = observability.traceRetriever(
                "Oracle destination vector + spatial search",
                parameters("entity.type", "destination", "location", nearDestinationName, "radius_km", radius),
                () -> destinationRepository.searchTop5ByEmbeddingNearLocation(
                        queryVector, point.x(), point.y(), radius),
                destination -> destinationDocument(destination, destination.vectorDistance()));

        if (results.isEmpty()) {
            return "No destinations found within " + radius + " km of " + nearDestinationName + " matching: " + query;
        }
        StringBuilder sb = new StringBuilder("Found nearby destinations:\n");
        for (DestinationSearchResult d : results) {
            sb.append(String.format(Locale.ROOT,
                "- %s (ID:%d, %s, cosine distance %.3f; lower is closer): %s\n",
                d.name(), d.id(), d.region(), d.vectorDistance(), d.description()));
        }
        return sb.toString();
    }

    @Tool("Search for hotels when there is no location constraint. Optional filters: destinationId, maxPrice (CHF/night); use null or 0 when an optional filter is not specified. For 'in', 'near', 'around', or 'within km of' requests, use searchNearbyHotels instead.")
    public String searchHotels(String query, Long destinationId, Double maxPrice) {
        return observability.traceTool("searchHotels", parameters(
                        "query", query, "destinationId", destinationId, "maxPrice", maxPrice),
                () -> doSearchHotels(query, destinationId, maxPrice));
    }

    private String doSearchHotels(String query, Long destinationId, Double maxPrice) {
        Vector queryVector = embedding(query, "hotels");
        Long effectiveDestinationId = positiveOrNull(destinationId);
        Double effectiveMaxPrice = positiveOrNull(maxPrice);
        List<HotelEntity> results;
        results = observability.traceRetriever(
                "Oracle hotel vector search",
                parameters("entity.type", "hotel", "destination_id", effectiveDestinationId,
                        "max_price_chf", effectiveMaxPrice, "max_distance", MAX_COSINE_DISTANCE),
                () -> {
                    if (effectiveDestinationId != null && effectiveMaxPrice != null) {
                        return hotelRepository.findTop5ByDestinationIdAndPricePerNightLessThanEqualsAndContentEmbeddingNear(
                                effectiveDestinationId, effectiveMaxPrice, queryVector, MAX_COSINE_DISTANCE);
                    } else if (effectiveDestinationId != null) {
                        return hotelRepository.findTop5ByDestinationIdAndContentEmbeddingNear(
                                effectiveDestinationId, queryVector, MAX_COSINE_DISTANCE);
                    } else if (effectiveMaxPrice != null) {
                        return hotelRepository.findTop5ByPricePerNightLessThanEqualsAndContentEmbeddingNear(
                                effectiveMaxPrice, queryVector, MAX_COSINE_DISTANCE);
                    }
                    return hotelRepository.findTop5ByContentEmbeddingNear(queryVector, MAX_COSINE_DISTANCE);
                },
                hotel -> hotelDocument(hotel, cosineDistance(queryVector, hotel.contentEmbedding())));
        if (results.isEmpty()) {
            return "No hotels found matching: " + query;
        }
        StringBuilder sb = new StringBuilder("Found hotels:\n");
        for (HotelEntity h : results) {
            sb.append(String.format("- %s (ID:%d, CHF %.0f/night): %s\n", h.name(), h.id(), h.pricePerNight(), h.description()));
        }
        return sb.toString();
    }

   @Tool("""
    Search for hotels matching a preference near a named destination in the catalog.
    Use when the user specifies a location such as 'in', 'near', 'around', or 'within'.
    radiusKm is in kilometers and defaults to 15.
    maxPrice is in CHF per night; use 0 when no budget is specified.
    """)
    public String searchNearbyHotels(String query, String nearDestinationName, Double radiusKm, Double maxPrice) {
        return observability.traceTool("searchNearbyHotels", parameters(
                        "query", query, "nearDestinationName", nearDestinationName,
                        "radiusKm", radiusKm, "maxPrice", maxPrice),
                () -> doSearchNearbyHotels(query, nearDestinationName, radiusKm, maxPrice));
    }

    private String doSearchNearbyHotels(String query, String nearDestinationName, Double radiusKm, Double maxPrice) {
        Optional<Point> location = locationForDestination(nearDestinationName);
        if (location.isEmpty()) {
            return unsupportedLocation("nearby hotel search", nearDestinationName);
        }

        double radius = radiusOrDefault(radiusKm, DEFAULT_HOTEL_RADIUS_KM);
        Point point = location.get();
        Vector queryVector = embedding(query, "hotels");
        Double effectiveMaxPrice = positiveOrNull(maxPrice);
        List<SearchResult<HotelEntity>> results = observability.traceRetriever(
                "Oracle hotel vector + spatial search",
                parameters("entity.type", "hotel", "location", nearDestinationName,
                        "radius_km", radius, "max_price_chf", effectiveMaxPrice),
                () -> hotelRepository
                        .searchTop5ByContentEmbeddingNearAndLocationNearAndPricePerNightLessThanEquals(
                            queryVector,
                            new Score(MAX_COSINE_DISTANCE),
                            point,
                            radius * 1_000,
                            effectiveMaxPrice == null ? UNBOUNDED_HOTEL_PRICE : effectiveMaxPrice)
                        .results(),
                result -> hotelDocument(result.entity(), result.score().value()));

        if (results.isEmpty()) {
            return "No hotels found within " + radius + " km of " + nearDestinationName + " matching: " + query;
        }
        StringBuilder sb = new StringBuilder("Found nearby hotels:\n");
        for (SearchResult<HotelEntity> result : results) {
            HotelEntity h = result.entity();
            sb.append(String.format("- %s (ID:%d, %s, CHF %.0f/night): %s\n",
                h.name(),
                h.id(),
                destinationName(h.destinationId()),
                h.pricePerNight(),
                h.description()
            ));
        }
        return sb.toString();
    }

    @Tool("Search for activities when there is no location constraint. Optional filter: destinationId. For 'in', 'near', 'around', or 'within km of' requests, use searchNearbyActivities instead.")
    public String searchActivities(String query, Long destinationId) {
        return observability.traceTool("searchActivities", parameters(
                        "query", query, "destinationId", destinationId),
                () -> doSearchActivities(query, destinationId));
    }

    private String doSearchActivities(String query, Long destinationId) {
        Vector queryVector = embedding(query, "activities");
        Long effectiveDestinationId = positiveOrNull(destinationId);
        List<ActivityEntity> results = observability.traceRetriever(
                "Oracle activity vector search",
                parameters("entity.type", "activity", "destination_id", effectiveDestinationId,
                        "max_distance", MAX_COSINE_DISTANCE),
                () -> effectiveDestinationId == null
                        ? activityRepository.findTop5ByContentEmbeddingNear(queryVector, MAX_COSINE_DISTANCE)
                        : activityRepository.findTop5ByDestinationIdAndContentEmbeddingNear(
                                effectiveDestinationId, queryVector, MAX_COSINE_DISTANCE),
                activity -> activityDocument(activity, cosineDistance(queryVector, activity.contentEmbedding())));
        if (results.isEmpty()) {
            return "No activities found matching: " + query;
        }
        StringBuilder sb = new StringBuilder("Found activities:\n");
        for (ActivityEntity a : results) {
            sb.append(String.format("- %s (ID:%d, %s): %s\n", a.name(), a.id(), a.season(), a.description()));
        }
        return sb.toString();
    }

    @Tool("Search for activities by preference near a location anchor. Supported anchors: Zermatt, Interlaken, Lucerne, Lausanne, St. Moritz, Lugano, Zurich. radiusKm defaults to 40.")
    public String searchNearbyActivities(String query, String nearDestinationName, Double radiusKm) {
        return observability.traceTool("searchNearbyActivities", parameters(
                        "query", query, "nearDestinationName", nearDestinationName, "radiusKm", radiusKm),
                () -> doSearchNearbyActivities(query, nearDestinationName, radiusKm));
    }

    private String doSearchNearbyActivities(String query, String nearDestinationName, Double radiusKm) {
        Optional<Point> location = locationForDestination(nearDestinationName);
        if (location.isEmpty()) {
            return unsupportedLocation("nearby activity search", nearDestinationName);
        }

        double radius = radiusOrDefault(radiusKm, DEFAULT_ACTIVITY_RADIUS_KM);
        Point point = location.get();
        Vector queryVector = embedding(query, "activities");
        List<SearchResult<ActivityEntity>> results = observability.traceRetriever(
                "Oracle activity vector + spatial search",
                parameters("entity.type", "activity", "location", nearDestinationName, "radius_km", radius),
                () -> activityRepository.searchTop5ByContentEmbeddingNearAndLocationNear(
                        queryVector, new Score(MAX_COSINE_DISTANCE), point, radius * 1_000).results(),
                result -> activityDocument(result.entity(), result.score().value()));

        if (results.isEmpty()) {
            return "No activities found within " + radius + " km of " + nearDestinationName + " matching: " + query;
        }
        StringBuilder sb = new StringBuilder("Found nearby activities:\n");
        for (SearchResult<ActivityEntity> result : results) {
            ActivityEntity a = result.entity();
            sb.append(String.format("- %s (ID:%d, %s, %s): %s\n",
                a.name(),
                a.id(),
                destinationName(a.destinationId()),
                a.season(),
                a.description()
            ));
        }
        return sb.toString();
    }

    @Tool("Add a specific item to this conversation's wishlist only when the user explicitly asks to add, save, bookmark, or place that item on their wishlist. If the item is ambiguous, ask which one instead of calling this tool. itemType: 'destination', 'hotel', or 'activity'. itemId: from search results.")
    public String addToWishlist(@ToolMemoryId String conversationId, String itemType, Long itemId) {
        return observability.traceTool("addToWishlist", parameters(
                        "conversationId", conversationId, "itemType", itemType, "itemId", itemId),
                () -> doAddToWishlist(conversationId, itemType, itemId));
    }

    private String doAddToWishlist(String conversationId, String itemType, Long itemId) {
        String type = itemType.toLowerCase();
        String name = switch (type) {
            case "destination" -> {
                Optional<DestinationEntity> d = destinationRepository.findById(itemId);
                yield d.map(DestinationEntity::name).orElse(null);
            }
            case "hotel" -> {
                Optional<HotelEntity> h = hotelRepository.findById(itemId);
                yield h.map(HotelEntity::name).orElse(null);
            }
            case "activity" -> {
                Optional<ActivityEntity> a = activityRepository.findById(itemId);
                yield a.map(ActivityEntity::name).orElse(null);
            }
            default -> null;
        };
        if (name == null) {
            return "Error: " + itemType + " with ID " + itemId + " not found.";
        }
        if (!wishlistRepository.save(conversationId, new WishlistItem(type, itemId))) {
            return "Error: could not save " + name + " to the wishlist.";
        }
        return "Wishlist confirmed: " + name
            + " is on your wishlist. No duplicate was created.";
    }

    @Tool("Get this conversation's wishlist with all saved destinations, hotels, and activities.")
    public String getWishlist(@ToolMemoryId String conversationId) {
        return observability.traceTool("getWishlist", parameters("conversationId", conversationId),
                () -> doGetWishlist(conversationId));
    }

    private String doGetWishlist(String conversationId) {
        List<WishlistItem> items = wishlistRepository.findAll(conversationId);
        if (items.isEmpty()) {
            return "Your wishlist is empty.";
        }
        StringBuilder sb = new StringBuilder("Your wishlist:\n");
        for (WishlistItem item : items) {
            sb.append("- ").append(formatWishlistItem(item)).append("\n");
        }
        return sb.toString();
    }

    private String formatWishlistItem(WishlistItem item) {
        if (item.name() == null) {
            return switch (item.itemType()) {
                case "destination" -> "Unknown destination";
                case "hotel" -> "Unknown hotel";
                case "activity" -> "Unknown activity";
                default -> "Unknown item";
            };
        }
        if (item.detail() == null || item.detail().isBlank()) {
            return item.name();
        }
        return switch (item.itemType()) {
            case "hotel" -> item.name() + " - " + item.detail();
            case "destination", "activity" -> item.name() + " (" + item.detail() + ")";
            default -> item.name();
        };
    }

    private Vector embedding(String query, String defaultQuery) {
        String effectiveQuery = query == null || query.isBlank() ? defaultQuery : query;
        return new FloatVector(embeddingService.generateEmbedding(effectiveQuery));
    }

    private Optional<Point> locationForDestination(String destinationName) {
        if (destinationName == null || destinationName.isBlank()) {
            return Optional.empty();
        }
        Optional<DestinationEntity> exact = destinationRepository.findByNameEqualsIgnoreCase(destinationName);
        if (exact.isPresent()) {
            return Optional.ofNullable(exact.get().location());
        }

        String normalized = destinationName.toLowerCase(Locale.ROOT);
        return destinationRepository.findAll()
            .stream()
            .filter(destination -> destination.name().toLowerCase(Locale.ROOT).contains(normalized))
            .map(DestinationEntity::location)
            .filter(point -> point != null)
            .findFirst();
    }

    private String destinationName(Long destinationId) {
        return destinationRepository.findById(destinationId)
            .map(DestinationEntity::name)
            .orElse("Unknown destination");
    }

    private String unsupportedLocation(String searchType, String locationName) {
        return "Unknown destination for " + searchType + ": " + locationName
            + ". Supported location anchors: " + supportedLocationAnchors() + ".";
    }

    private String supportedLocationAnchors() {
        return destinationRepository.findAll()
            .stream()
            .map(DestinationEntity::name)
            .sorted()
            .reduce((left, right) -> left + ", " + right)
            .orElse("none");
    }

    private double radiusOrDefault(Double radiusKm, double defaultRadiusKm) {
        if (radiusKm == null || radiusKm <= 0) {
            return defaultRadiusKm;
        }
        return radiusKm;
    }

    private Long positiveOrNull(Long value) {
        return value == null || value <= 0 ? null : value;
    }

    private Double positiveOrNull(Double value) {
        return value == null || value <= 0 ? null : value;
    }

    private AiObservability.RetrievalDocument destinationDocument(
            DestinationEntity destination,
            Double vectorDistance) {
        return retrievalDocument(
                "destination",
                destination.id(),
                destination.name() + " (" + destination.region() + "): " + destination.description(),
                vectorDistance,
                parameters("region", destination.region()));
    }

    private AiObservability.RetrievalDocument destinationDocument(
            DestinationSearchResult destination,
            Double vectorDistance) {
        return retrievalDocument(
                "destination",
                destination.id(),
                destination.name() + " (" + destination.region() + "): " + destination.description(),
                vectorDistance,
                parameters("region", destination.region()));
    }

    private AiObservability.RetrievalDocument hotelDocument(HotelEntity hotel, Double vectorDistance) {
        return retrievalDocument(
                "hotel",
                hotel.id(),
                hotel.name() + " (CHF " + Math.round(hotel.pricePerNight()) + "/night): " + hotel.description(),
                vectorDistance,
                parameters("destination_id", hotel.destinationId(), "price_chf", hotel.pricePerNight()));
    }

    private AiObservability.RetrievalDocument activityDocument(ActivityEntity activity, Double vectorDistance) {
        return retrievalDocument(
                "activity",
                activity.id(),
                activity.name() + " (" + activity.season() + "): " + activity.description(),
                vectorDistance,
                parameters("destination_id", activity.destinationId(), "season", activity.season()));
    }

    private AiObservability.RetrievalDocument retrievalDocument(
            String entityType,
            Long id,
            String content,
            Double vectorDistance,
            Map<String, Object> details) {
        Map<String, Object> metadata = new LinkedHashMap<>();
        metadata.put("entity_type", entityType);
        if (vectorDistance != null) {
            metadata.put("cosine_distance", vectorDistance);
        }
        metadata.putAll(details);
        Double score = vectorDistance == null ? null : Math.max(-1.0, Math.min(1.0, 1.0 - vectorDistance));
        return new AiObservability.RetrievalDocument(entityType + ":" + id, content, score, metadata);
    }

    private Double cosineDistance(Vector queryVector, Vector contentVector) {
        if (queryVector == null || contentVector == null) {
            return null;
        }
        float[] query = queryVector.toFloatArray();
        float[] content = contentVector.toFloatArray();
        if (query.length == 0 || query.length != content.length) {
            return null;
        }
        double dotProduct = 0;
        double queryNorm = 0;
        double contentNorm = 0;
        for (int index = 0; index < query.length; index++) {
            dotProduct += query[index] * content[index];
            queryNorm += query[index] * query[index];
            contentNorm += content[index] * content[index];
        }
        if (queryNorm == 0 || contentNorm == 0) {
            return null;
        }
        double similarity = dotProduct / (Math.sqrt(queryNorm) * Math.sqrt(contentNorm));
        return 1.0 - Math.max(-1.0, Math.min(1.0, similarity));
    }

    private Map<String, Object> parameters(Object... namesAndValues) {
        Map<String, Object> parameters = new LinkedHashMap<>();
        for (int i = 0; i < namesAndValues.length; i += 2) {
            if (namesAndValues[i + 1] != null) {
                parameters.put(namesAndValues[i].toString(), namesAndValues[i + 1]);
            }
        }
        return parameters;
    }

}
