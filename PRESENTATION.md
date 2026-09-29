# Swiss Travel Advisor

GraalVM · Micronaut · LangChain4j · Oracle AI Database 

---

## What we will cover

- A practical strategy for generating vector embeddings
- Combining similarity with application data and business constraints
- Combining semantic and location-aware search
- The Micronaut Data and Micronaut LangChain4j features that help most
- Building and running the application as a GraalVM Native Image

All five ideas come together in one small travel application.

---

## What we are building

Ask:

> Find a quiet lakeside hotel near Lucerne under CHF 250.

Then continue naturally:

> Save the first one to my wishlist.

The assistant searches by meaning, respects real constraints, remembers the conversation, and performs only operations we explicitly allow.

---

## The whole idea in one picture

```text
User
  ↓
Micronaut HTTP API
  ↓
AI chooses a typed Java tool
  ↓
Oracle filters and ranks real application data
  ↓
AI explains the result
```

The AI understands intent. Java controls actions. Oracle decides which rows qualify.

---

## One sentence contains three different searches

> “A **quiet lakeside** hotel **near Lucerne** **under CHF 250**”

| User means | Best operation |
|---|---|
| quiet lakeside | Vector similarity |
| near Lucerne | Spatial radius |
| under CHF 250 | Numeric filter |

A vector is excellent at meaning. It is the wrong tool for exact distance or price.

---

## What Micronaut contributes

Micronaut turns the idea into a normal Java application:

- `@Controller` exposes the HTTP API.
- `@AiService` creates the assistant implementation.
- `@Tool` exposes only approved operations to the model.
- Micronaut Data creates repository implementations.
- Compile-time metadata keeps runtime work small and Native Image friendly.

---

## The assistant is just an interface

```java
@AiService(tools = TravelTools.class)       // declares an injectable AI service
public interface SwissTravelAssistant {

    String chat(
        @MemoryId String conversationId,    // selects conversation memory
        @UserMessage String message         // sent to the model
    );
}
```

LangChain4j manages model and tool calls, so there is no hand-written tool-calling loop.

Code: [SwissTravelAssistant.java](src/main/java/com/example/service/SwissTravelAssistant.java)

---

## A tool is a safe doorway into the application

```java
@Tool("Search for hotels by preference near a location anchor") // tells the model when to use it
public String searchNearbyHotels(
    String query,                 // semantic preference
    String nearDestinationName,   // location anchor
    Double radiusKm,              // exact radius
    Double maxPrice               // exact price
) {
    return doSearchNearbyHotels(
        query, nearDestinationName, radiusKm, maxPrice); // validated Java path
}
```

Java validates inputs and calls known repository code.

The model never receives database credentials and never writes arbitrary SQL.

Code: [TravelTools.java](src/main/java/com/example/tools/TravelTools.java)

---

## Oracle stores meaning beside the facts

```sql
CREATE TABLE hotels (
    id                    NUMBER PRIMARY KEY,
    destination_id        NUMBER NOT NULL,          -- relationship
    price_per_night       NUMBER(10, 2) NOT NULL,   -- exact fact
    description           CLOB NOT NULL,            -- source text
    description_embedding VECTOR(1536, FLOAT32)     -- meaning
);
```

This is the key Oracle AI Database advantage in the demo: vectors are not kept in a separate system. They live beside the relational data they describe.

Code: [V1__create_schema.sql](src/main/resources/db/migration/V1__create_schema.sql)

---

## The Java model tells the same story

```java
@MappedEntity("hotels")
public record HotelEntity(
    Long id,
    Double pricePerNight,              // exact filter
    String description,
    FloatVector descriptionEmbedding,  // semantic ranking
    @Srid(4326) Point location          // geographic filter
) {}
```

Micronaut Data maps application-level vector and geometry types to database-native values. `@Srid(4326)` makes the coordinate system explicit.

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
    findTop5ByPricePerNightLessThanEqualsAndDescriptionEmbeddingNear(
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
ORDER BY VECTOR_DISTANCE(description_embedding,             -- semantic ranking
                         :query_vector, COSINE)
FETCH FIRST 5 ROWS ONLY;                                    -- bounded model context
```

Code: [HotelRepository.java](src/main/java/com/example/repository/HotelRepository.java)

---

## A simple SQL Developer check

For a quick live database demo, open the `MT ADB Alina Production` connection in
SQL Developer for VS Code and open a SQL Worksheet or SQL Notebook. In the
seeded catalog, Lucerne has destination ID `3`:

```sql
SELECT
    d.name AS destination,
    h.name AS hotel,
    h.price_per_night
FROM hotels h
JOIN destinations d
    ON d.id = h.destination_id
WHERE d.id = 3
  AND h.price_per_night <= 250
ORDER BY h.price_per_night;
```

This shows a normal relational join and a hard business filter. Change `250`
to `350` and run it again to show how the result set changes. The application
then builds on this same relational data with Oracle Spatial filtering and
vector ranking.

---

## Why one database matters

```text
Oracle row
├── relational facts   price, IDs, season
├── spatial value      SDO_GEOMETRY
└── semantic value     VECTOR
```

One query means:

- no copied catalog in a separate vector store;
- no client-side filtering after retrieval;
- no disagreement between “AI data” and application data;
- one consistency boundary for search and state.

Vector search is an operator inside the application—not a second application.

---

## A practical embedding strategy

```java
String text = hotel.name()
    + " in " + hotel.destinationName()
    + ". " + hotel.description();

float[] embedding = embeddingService.generateEmbedding(text);
```

Embed descriptive content that changes slowly.

Do **not** embed current price, wishlist state, permissions, or geographic radius. Those values remain exact and queryable.

In production, also store the embedding model, dimensions, source hash, and generation time so vectors can be refreshed safely.

Code: [DataInitializer.java](src/main/java/com/example/service/DataInitializer.java)

---

## The assistant can act—but only with permission

```java
@Tool("Add an item only when the user explicitly asks to save it")
public String addToWishlist(
    @ToolMemoryId String conversationId, // isolates each conversation
    String itemType,
    Long itemId                          // must resolve to a real row
) {
    // validate type → resolve ID → save without duplicates
}
```

The model proposes the action. Java checks the item. Oracle persists it. A database `MERGE` makes repeated requests idempotent.

Code: [TravelTools.java](src/main/java/com/example/tools/TravelTools.java) · [WishlistRepository.java](src/main/java/com/example/repository/WishlistRepository.java)

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

## Added since the original version

The first version demonstrated embeddings, vector search, tools, and a wishlist. The current application also has:

- vector + spatial + price search in one Oracle query;
- Oracle-backed conversation memory and conversation-scoped wishlists;
- a browser chat interface and conversation history;
- OpenTelemetry/OpenInference traces for the agent, model, tools, embeddings, and retrieval;
- small deterministic evaluations for tool choice and safe wishlist mutation;
- explicit GraalVM reachability metadata and a native build report profile.

This is now an observable application, not only a vector-search sample.

---

## You can see how the answer happened

```text
AGENT  "Find a quiet hotel near Lucerne..."
├── LLM        chooses searchNearbyHotels
├── TOOL       validates structured arguments
│   ├── EMBEDDING   creates the query vector
│   └── RETRIEVER   runs the Oracle query
└── LLM        explains the returned rows
```

The trace records the selected tool, constraints, model latency, token use, result count, and failures.

That makes “the AI gave a strange answer” a debuggable engineering problem.

Code: [AiObservability.java](src/main/java/com/example/observability/AiObservability.java)

---

## A `ChatModelListener` makes the AI loop visible

The model is not a black box during a demo. `TravelAdvisorChatModelLogger` listens to each LangChain4j chat request and response, then prints a compact, human-readable transcript:

```text
USER       let's save Lucerne Palace Hotel to my wishlist
TOOLS      addToWishlist, searchNearbyHotels, searchHotels,
           searchNearbyActivities, searchActivities,
           searchNearbyDestinations, searchDestinations, getWishlist
TOOL CALL  addToWishlist({"itemType":"hotel","itemId":5})
TOOL RESULT Added to wishlist: Lucerne Palace Hotel
ASSISTANT [1547 tok] Added Lucerne Palace Hotel to your wishlist! ✨
```

This captured exchange shows several useful things at once:

- the model sees a controlled, explicit tool catalog rather than arbitrary Java methods;
- it uses the hotel ID from the earlier search result instead of inventing a new record;
- the write is visible as a structured call with typed arguments;
- the application result comes back to the model before the friendly response is generated.

The listener logs the user message and available tools in `onRequest`, tool calls and token usage in `onResponse`, and tool results on the next model request. This makes an agent decision easy to inspect in a terminal or demo recording:

```text
user request → available tools → structured tool call
            → application result → final assistant response
```

Code: [TravelAdvisorChatModelLogger.java](src/main/java/com/example/logging/TravelAdvisorChatModelLogger.java)

---

# What is especially interesting in Micronaut 5.2?

---

## Micronaut 5.2 + Oracle: database-aware APIs

Micronaut Data 5.2 brings several Oracle-focused capabilities into one compile-time data layer:

| Feature | Why it is interesting |
|---|---|
| Vector + spatial mapping | Use `Vector`, `Point`, `@Srid`, and derived search methods |
| Upserts | Generate Oracle `MERGE` from a repository method |
| Commit-outcome recovery | Resolve “did my commit succeed?” after a lost connection |
| Sessionless transactions | Suspend work and resume it in a later request or connection |
| Lock-free reservations | Model high-contention inventory without row locking |
| Native `BOOLEAN` support | Target modern Oracle boolean columns directly |

The travel demo does not need all of these. Booking, inventory, and payment workflows might.

Upserts, commit recovery, sessionless transactions, lock-free reservations, and native Oracle `BOOLEAN` targeting are new in the 5.2 line. Vector and spatial mapping were already available and are central to this application.

Source: [Micronaut 5.2 release](https://micronaut.io/2026/09/27/micronaut-framework-5-2-0/) · [Micronaut Data documentation](https://docs.micronaut.io/5.2.x/data/)

---

## 5.2 feature: recover an ambiguous Oracle commit

Imagine the database commits a booking, but the network drops before the application receives the acknowledgement.

```java
@OracleTransactional
@OracleTransactional.Recoverable(maxAttempts = 2) // Micronaut Data 5.2
public Booking confirm(Booking booking) {
    return bookingRepository.save(booking);
}
```

Micronaut can ask Oracle for the transaction outcome instead of blindly repeating the write or reporting a false failure.

**Where it fits:** booking or payment confirmation.

**In this demo:** an optional next step, not currently used. It requires Oracle Transaction Guard on the database service and `enable-oracle-transaction-recovery=true` on the datasource.

---

## 5.2 feature: an Oracle transaction across requests

```java
@OracleTransactional(
    sessionless = OracleTransactional.Sessionless.SUSPEND,
    timeout = 60
)
public Long holdRoom(Room room) { ... }

@OracleTransactional(
    sessionless = OracleTransactional.Sessionless.REQUIRES_SUSPENDED
)
public void confirmRoom(Long id) { ... }
```

The first call starts work and suspends it. A later call resumes the same Oracle transaction and completes it—even on another JDBC connection.

```text
Request A: BEGIN → hold room → SUSPEND → return GTRID
Request B: receive GTRID → RESUME → confirm → COMMIT
```

**Why it is cool:** the transaction survives between requests without keeping one pooled JDBC connection checked out. Micronaut can propagate the transaction ID in an HTTP header.

**Caution:** suspended changes are not visible until the resumed transaction commits.

Source: [Oracle sessionless transactions in Micronaut Data](https://docs.micronaut.io/5.2.x/data/#oracleSessionlessTransactions)

---

## 5.2 feature: simpler idempotent writes

The wishlist uses Micronaut Data 5.2 to generate an Oracle `MERGE` from a repository method:

```java
@JdbcRepository(dialect = Dialect.ORACLE)       // generate Oracle SQL
interface WishlistWriteRepository {

    @Upsert(conflictsOn = {
        "conversationId", "itemType", "itemId"
    })                                           // match the unique key
    void upsert(WishlistItemEntity item);         // generates MERGE
}
```

That is a natural fit for tools because models, users, and networks may repeat a request.

The existing wishlist service still owns reads and error handling; only its write path delegates to the generated repository.

Code: [WishlistWriteRepository.java](src/main/java/com/example/repository/WishlistWriteRepository.java) · [WishlistItemEntity.java](src/main/java/com/example/entity/WishlistItemEntity.java)

Source: [Micronaut Data `@Upsert`](https://micronaut-projects.github.io/micronaut-data/5.2.0/api/io/micronaut/data/annotation/Upsert.html)

---

## Why Micronaut and GraalVM fit together

They optimize different stages of the same application lifecycle. Micronaut generates framework code and metadata first; GraalVM then analyzes and compiles the prepared application as a whole.

```text
Java source and annotations
            │
            ▼
Micronaut compilation
  • generate bean definitions and injection metadata
  • generate repository implementations and queries
  • generate serializers and deserializers
  • generate AI-service bean and proxy metadata
            │
            ▼
GraalVM Native Image build
  • analyze reachable code
  • compile reachable code ahead of time
  • apply reachability metadata for resources, reflection, and proxies
  • produce a platform-specific native executable
            │
            ▼
Application runtime
  • create and inject beans
  • execute database queries
  • read and write JSON
  • create AI services and call models and tools
```

Runtime work does not disappear: database queries, HTTP requests, embeddings, and model calls still happen while handling requests. The benefit is that startup requires less framework discovery, reflection, and class loading.

Micronaut 5.2 aligns the platform with GraalVM 25.4.4.1.1; this project targets JDK 25.

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

## Demo flow

### 1. Combine meaning with hard constraints

> Find a quiet lakeside hotel near Lucerne under CHF 250.

### 2. Perform an explicit action

> Save the first hotel to my wishlist.

### 3. Prove that state persists

> What is on my wishlist?

### 4. Inspect the trace

Show the model → tool → embedding → Oracle retrieval path and its evaluation results.

Runbook: [demo.md](demo.md)

---

## Takeaways

1. Let the model interpret language—not enforce business rules.
2. Keep exact facts exact; use vectors for meaning and ranking.
3. Oracle can combine relational, spatial, and vector operations in one query.
4. Micronaut makes AI services and data access feel like typed Java.
5. Micronaut 5.2 adds unusually deep Oracle transaction features.
6. Compile-time Micronaut fits naturally with GraalVM Native Image.


---
