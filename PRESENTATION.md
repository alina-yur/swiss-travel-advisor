# Swiss Travel Advisor

---

## Application

- Web app with a `/api/chat` endpoint.
- Startup loads destinations, hotels, and activities, then generates embeddings.
- Database stores vectors beside the source data.
- Each question is embedded and searched against the catalog.
- OpenAI selects tools; LangChain4j routes calls and messages.

---

### Stack

- GraalVM — Native Image compilation
- Micronaut — lightweight JVM framework with compile-time dependency injection
- LangChain4j — LLM orchestration and tool calling
- Oracle Database — vector storage and similarity search
- OpenAI — models for chat and embeddings

---

## One user request can result in three different searches

> “Recommend a quiet lakeside hotel near Lucerne under CHF 250”

| User means | Best operation |
|---|---|
| quiet lakeside | Vector similarity |
| near Lucerne | Spatial radius |
| under CHF 250 | Numeric filter |


---

## Web aplication

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

```java
SearchResults<HotelEntity>
searchTop5ByContentEmbeddingNearAndLocationNearAndPricePerNightLessThanEquals(
    Vector embedding,
    Score maxDistance,
    Point location,
    double radiusMeters,
    Double maxPrice
);
```

Micronaut Data generates the cosine ranking, `SDO_WITHIN_DISTANCE`, price
predicate, and top-five limit at compile time. No handwritten hotel search SQL
is required.

At a high level, Micronaut Data reads the repository method name during
compilation, validates its properties and parameter types, and generates the
Oracle query implementation. There is no runtime method-name parsing.

```text
HotelRepository.java
        ↓ compile time
target/classes/com/example/repository/
    $HotelRepository$Intercepted$Definition$Exec.class
        ↓ runtime
Oracle AI Database
```

Conceptually, the generated query is:

```sql
SELECT ..., VECTOR_DISTANCE(...) AS mn_score
FROM hotels
WHERE VECTOR_DISTANCE(content_embedding, ?, COSINE) <= ?
  AND SDO_WITHIN_DISTANCE(location, ?, 'distance=' || ?) = 'TRUE'
  AND price_per_night <= ?
ORDER BY VECTOR_DISTANCE(content_embedding, ?, COSINE)
FETCH NEXT 5 ROWS ONLY
```

The generated implementation performs semantic ranking while Oracle applies
the geographic radius and maximum-price constraints before returning the five
closest matches. The SQL above is intentionally abbreviated; the generated
class contains the complete dialect-specific statement and parameter mapping.

[HotelRepository.java](src/main/java/com/example/repository/HotelRepository.java) · [HotelEntity.java](src/main/java/com/example/entity/HotelEntity.java)

---


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

## Reading annotation scores

These scores are **pass rates for simple checks**, not model confidence scores.

```text
0.00 = the check failed for every evaluated trace
1.00 = the check passed for every evaluated trace
0.83 = the check passed for 83% of evaluated traces
```

| Annotation | Simple meaning |
|---|---|
| `response_present` | Did the assistant return a non-empty answer? |
| `tool_selection` | Did it call the search tool expected for that request? |
| `user_feedback` | Did users who submitted feedback mark the answer as helpful? |
| `wishlist_permission` | Did the application avoid changing the wishlist without an explicit request? |

For the example dashboard:

```text
response_present     1.00  Every evaluated request received an answer.
tool_selection       0.83  The expected tool was used 83% of the time.
user_feedback        1.00  All submitted ratings in this period were positive.
wishlist_permission  1.00  No unauthorized wishlist change was detected.
```

### How to explain it on stage

> “These lines turn important application behavior into measurable checks. A
> score of one means every evaluated trace passed. Tool selection is at 0.83,
> so that is the line I would investigate: I can open the failing trace and see
> the user request, the tool the model chose, and the tool our rule expected.”

Only traces that have a particular annotation contribute to that annotation's
average. For example, `user_feedback = 1.00` means all **submitted ratings**
were positive; it does not mean every user submitted feedback. Likewise,
`response_present = 1.00` confirms that answers were returned, not that every
answer was correct.

The code-based checks are intentionally deterministic and easy to explain.
`tool_selection` uses demo-specific keyword rules, so a low score can indicate
either a model/tool-routing problem or a rule that needs refinement. Open the
individual trace and read its annotation explanation before drawing a
conclusion from the average.

Code: [AiObservability.java](src/main/java/com/example/observability/AiObservability.java) · [PhoenixAnnotationPublisher.java](src/main/java/com/example/observability/PhoenixAnnotationPublisher.java)

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
