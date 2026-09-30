# Swiss Travel Advisor

---

### Stack

- Micronaut — lightweight JVM framework with compile-time dependency injection
- GraalVM — native-image compilation
- LangChain4j — LLM orchestration and tool calling
- Oracle Database — vector storage and similarity search
- OpenAI — models for chat and embeddings


---


## Application

- Web app with a `/api/chat` endpoint.
- Startup loads destinations, hotels, and activities, then generates embeddings.
- Database stores vectors beside the source data.
- Each question is embedded and searched against the catalog.
- OpenAI selects tools; LangChain4j routes calls and messages.

---

## One user request can result in three different searches

> “Recommend a quiet lakeside hotel near Lucerne under CHF 250”

| User means | Best operation |
|---|---|
| quiet lakeside | Vector similarity |
| near Lucerne | Spatial radius |
| under CHF 250 | Numeric filter |


---

## What Micronaut contributes

Micronaut turns the idea into a normal Java application:

- `@Controller` exposes the HTTP API.
- `@AiService` creates the assistant implementation.
- Micronaut Data creates repository implementations.
- Compile-time metadata keeps runtime work relatively small and Native Image friendly.

---

## The assistant is just an interface

```java
@AiService(tools = TravelTools.class)
public interface SwissTravelAssistant {

    String chat(
        @MemoryId String conversationId,
        @UserMessage String message
    );
}
```

LangChain4j manages model and tool calls.

[SwissTravelAssistant.java](src/main/java/com/example/service/SwissTravelAssistant.java)

---

## A tool is a safe doorway into the application

```java
@Tool("Search for hotels by preference near a location anchor")
public String searchNearbyHotels(
    String query,
    String nearDestinationName,
    Double radiusKm,    // default or user-specified
    Double maxPrice
) {
    return doSearchNearbyHotels(
        query, nearDestinationName, radiusKm, maxPrice);
}
```

The model never receives database credentials and never writes arbitrary SQL.

Code: [TravelTools.java](src/main/java/com/example/tools/TravelTools.java)

---

## Storing embeddings beside the factual information

```sql
CREATE TABLE hotels (
    id                    NUMBER PRIMARY KEY,
    destination_id        NUMBER NOT NULL,
    price_per_night       NUMBER(10, 2) NOT NULL,
    description           CLOB NOT NULL,
    content_embedding     VECTOR(1536, FLOAT32)
);
```

---

## The Java model tells the same story

```java
@MappedEntity("hotels")
public record HotelEntity(
    Long id,
    Double pricePerNight,              // exact filter
    String description,
    FloatVector contentEmbedding,  // semantic ranking
    @Srid(4326) Point location          // geographic filter
) {}
```

```text
Hotel
├── price: CHF 240
├── description: "quiet hotel beside the lake"
├── content embedding: numbers representing the searchable text
└── location: a point on a map
```

The description is the original text. The content embedding is a numeric representation generated from the name, destination, and description for similarity search.

Activity embeddings also include the season.

Micronaut Data maps application-level vector and geometry types to database-native values. `@Srid(4326)` makes the coordinate system explicit.

In this project:

```text
FloatVector → VECTOR
Point       → SDO_GEOMETRY
```

For example, `FloatVector contentEmbedding` maps to `content_embedding VECTOR(1536, FLOAT32)`, while `@Srid(4326) Point location` maps to an Oracle `MDSYS.SDO_GEOMETRY` value.

It can also translate spatial repository names into Oracle operations:

```text
findByLocationNear(point, distance)
                    ↓
SDO_WITHIN_DISTANCE(...)
```

This demo uses explicit SQL only where vector ranking, spatial distance, and an optional price filter must be composed in one statement.

Code: [HotelEntity.java](src/main/java/com/example/entity/HotelEntity.java)

---

## Vector search can read like a sentence

```java
@JdbcRepository(dialect = Dialect.ORACLE)          // Oracle SQL and type handling
interface HotelRepository {

    List<HotelEntity>
    findTop5ByPricePerNightLessThanEqualsAndContentEmbeddingNear(
        Double maxPrice,                            // hard price limit
        Vector queryEmbedding,                      // semantic query
        Double maxDistance                         // similarity threshold
    );
}
```

Micronaut Data parses and validates this repository method during compilation; `Top5` bounds the semantically ranked result.

Code: [HotelRepository.java](src/main/java/com/example/repository/HotelRepository.java)

---

## The centerpiece: one Oracle query

```sql
SELECT name, price_per_night, description
FROM hotels
WHERE price_per_night <= :max_price                         -- hard price filter
  AND SDO_WITHIN_DISTANCE(location, :lucerne,               -- radius filter
        'distance=' || :radius_km || ' unit=KM') = 'TRUE'
ORDER BY VECTOR_DISTANCE(content_embedding,                 -- semantic ranking
                         :query_vector, COSINE)
FETCH FIRST 5 ROWS ONLY;                                    -- bounded model context
```

Code: [HotelRepository.java](src/main/java/com/example/repository/HotelRepository.java)

---




## A practical embedding strategy

```java
String text = hotel.name()
    + " in " + hotel.destinationName()
    + ". " + hotel.description();

float[] embedding = embeddingService.generateEmbedding(text);
```

Embed descriptive content that changes slowly.

Do not embed current price, wishlist state, permissions, or geographic radius. Those values remain exact and queryable.

In practice, also store the embedding model, dimensions, source hash, and generation time so vectors can be refreshed safely.

Code: [DataInitializer.java](src/main/java/com/example/service/DataInitializer.java)

---

## Memory is real application data

```java
return MessageWindowChatMemory.builder()
    .id(memoryId)                       // conversation boundary
    .maxMessages(20)                    // bounded context
    .chatMemoryStore(oracleStore)       // survives restart
    .build();
```

Conversation history is serialized into Oracle. The wishlist uses the same conversation ID, so dialogue and saved state stay aligned.

Code: [OracleChatMemoryProvider.java](src/main/java/com/example/memory/OracleChatMemoryProvider.java) · [OracleChatMemoryStore.java](src/main/java/com/example/memory/OracleChatMemoryStore.java)

---

---

## Observe model calls with a `ChatModelListener`

`ChatModelListener` logs requests, available tools, responses, tool calls, token usage, and errors without changing the chat logic.

In this demo:

```text
[ai] tool call: addToWishlist({"itemType":"hotel","itemId":5})
[ai] tool result: addToWishlist -> Added to wishlist: Lucerne Palace Hotel
[ai] assistant [1547 tok]: Added Lucerne Palace Hotel to your wishlist! ✨
```

The application—not the model—produces the tool result and sends it back before the final response:

```text
request → tool call → application result → response
```

Code: [TravelAdvisorChatModelLogger.java](src/main/java/com/example/logging/TravelAdvisorChatModelLogger.java)

---

# What is especially interesting in Micronaut 5.2?


---

## Building the native executable

```bash
./mvnw package -Dpackaging=native-image 

./target/swiss-travel-advisor
```

---


## GraalVM gives us a build X-ray

```xml
<profile>
  <id>native-report</id>
  <buildArg>--emit=build-report</buildArg>
  <buildArg>-H:+ReportDynamicAccess</buildArg>
</profile>
```

The report shows what went into the binary: reachable code, resources, reflection, and image size contributors.

That is more useful than guessing why a native image is large or why a dynamic code path is missing.

Run with `-Pnative-report` when using a GraalVM distribution that supports build reports.

---


---

## Takeaways

1. Generate embeddings from stable descriptive content.
2. Keep price, location, and business constraints as queryable data.
3. Combine embedding-based search with exact price and location filters in one database query.
4. Use Micronaut Data and LangChain4j for typed repositories, AI services, and tools.
5. Use GraalVM Native Image to reduce application startup overhead and memory usage.


---
