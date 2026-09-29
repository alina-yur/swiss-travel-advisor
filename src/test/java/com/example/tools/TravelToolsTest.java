package com.example.tools;

import com.example.entity.DestinationEntity;
import com.example.entity.HotelEntity;
import com.example.model.DestinationSearchResult;
import com.example.observability.AiObservability;
import com.example.observability.PhoenixAnnotationPublisher;
import com.example.repository.ActivityRepository;
import com.example.repository.DestinationRepository;
import com.example.repository.HotelRepository;
import com.example.repository.SpatialSearchRepository;
import com.example.repository.WishlistRepository;
import com.example.service.EmbeddingService;
import io.micronaut.data.model.geo.Point;
import io.micronaut.data.model.vector.Vector;
import io.micronaut.serde.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TravelToolsTest {
    private final List<String> embeddedTexts = new ArrayList<>();
    private final List<DestinationEntity> destinations = new ArrayList<>();
    private List<HotelEntity> genericHotels = List.of();
    private List<HotelEntity> nearbyHotels = List.of();
    private List<DestinationSearchResult> nearbyDestinations = List.of();
    private NearbyHotelCall nearbyHotelCall;
    private String genericHotelMethod;
    private Double genericHotelDistance;
    private TravelTools tools;

    @BeforeEach
    void setUp() {
        EmbeddingService embeddings = new EmbeddingService(null, null) {
            @Override
            public float[] generateEmbedding(String text) {
                embeddedTexts.add(text);
                return new float[]{1.0f};
            }
        };
        DestinationRepository destinationRepository = proxy(DestinationRepository.class, (method, arguments) -> switch (method) {
            case "findByNameEqualsIgnoreCase" -> destinations.stream()
                .filter(destination -> destination.name().equalsIgnoreCase((String) arguments[0])).findFirst();
            case "findById" -> destinations.stream()
                .filter(destination -> destination.id().equals(arguments[0])).findFirst();
            case "findAll" -> List.copyOf(destinations);
            case "searchTop5ByEmbeddingNearLocation" -> nearbyDestinations;
            default -> defaultValue(method);
        });
        HotelRepository hotelRepository = proxy(HotelRepository.class, (method, arguments) -> {
            if (method.startsWith("findTop5")) {
                genericHotelMethod = method;
                genericHotelDistance = (Double) arguments[arguments.length - 1];
                return genericHotels;
            }
            return defaultValue(method);
        });
        SpatialSearchRepository spatialRepository = new SpatialSearchRepository(null) {
            @Override
            public List<HotelEntity> searchHotelsByVectorNear(
                    Vector embedding, double longitude, double latitude, double radiusKm, Double maxPrice) {
                nearbyHotelCall = new NearbyHotelCall(longitude, latitude, radiusKm, maxPrice);
                return nearbyHotels;
            }
        };
        AiObservability observability = new AiObservability(
            OpenTelemetry.noop(), ObjectMapper.getDefault(),
            new PhoenixAnnotationPublisher(ObjectMapper.getDefault(), false, "http://localhost:6006", ""),
            false, false, "text-embedding-3-small");

        tools = new TravelTools(
            embeddings,
            destinationRepository,
            hotelRepository,
            proxy(ActivityRepository.class, (method, arguments) -> defaultValue(method)),
            spatialRepository,
            new WishlistRepository(null),
            observability
        );
    }

    @Test
    void nearbyHotelSearchTreatsZeroMaxPriceAsNoBudgetFilter() {
        destinations.add(destination(1L, "Zermatt", 7.7491, 46.0207));
        nearbyHotels = List.of(hotel(1L, "Matterhorn View Hotel"));

        String result = tools.searchNearbyHotels("recommend hotels", "Zermatt", 15.0, 0.0);

        assertTrue(result.contains("Matterhorn View Hotel"));
        assertEquals(new NearbyHotelCall(7.7491, 46.0207, 15.0, null), nearbyHotelCall);
    }

    @Test
    void nearbyHotelSearchUsesDefaultQueryWhenModelSuppliesBlankQuery() {
        destinations.add(destination(3L, "Lucerne", 8.3093, 47.0502));
        nearbyHotels = List.of(hotel(3L, "Lake Lucerne Hotel"));

        String result = tools.searchNearbyHotels("", "Lucerne", 20.0, 0.0);

        assertTrue(result.contains("Lake Lucerne Hotel"));
        assertEquals(List.of("hotels"), embeddedTexts);
        assertEquals(new NearbyHotelCall(8.3093, 47.0502, 20.0, null), nearbyHotelCall);
    }

    @Test
    void genericHotelSearchTreatsZeroOptionalValuesAsUnspecified() {
        genericHotels = List.of(hotel(7L, "Zurich Old Town Boutique"));

        String result = tools.searchHotels("recommended hotels in Zurich", 0L, 0.0);

        assertTrue(result.contains("Zurich Old Town Boutique"));
        assertEquals("findTop5ByDescriptionEmbeddingNear", genericHotelMethod);
        assertEquals(2.0, genericHotelDistance);
    }

    @Test
    void nearbyDestinationSearchIncludesCosineDistance() {
        destinations.add(destination(3L, "Lucerne", 8.3093, 47.0502));
        nearbyDestinations = List.of(new DestinationSearchResult(
            3L, "Lucerne", "Central Switzerland", "Lake and mountains", 0.125));

        String result = tools.searchNearbyDestinations("quiet lakeside destination", "Lucerne", null);

        assertTrue(result.contains("cosine distance 0.125; lower is closer"));
    }

    private DestinationEntity destination(Long id, String name, double longitude, double latitude) {
        return new DestinationEntity(id, name, "Region", "Description", null, new Point(longitude, latitude));
    }

    private HotelEntity hotel(Long destinationId, String name) {
        return new HotelEntity(10L, destinationId, name, 240.0, "A comfortable hotel", null, null);
    }

    @SuppressWarnings("unchecked")
    private static <T> T proxy(Class<T> type, Invocation invocation) {
        return (T) Proxy.newProxyInstance(type.getClassLoader(), new Class<?>[]{type},
            (ignored, method, arguments) -> invocation.call(method.getName(), arguments));
    }

    private static Object defaultValue(String method) {
        if (method.equals("findAll") || method.startsWith("findTop")) {
            return List.of();
        }
        if (method.startsWith("find")) {
            return Optional.empty();
        }
        return null;
    }

    @FunctionalInterface
    private interface Invocation {
        Object call(String method, Object[] arguments);
    }

    private record NearbyHotelCall(double longitude, double latitude, double radiusKm, Double maxPrice) {
    }
}
