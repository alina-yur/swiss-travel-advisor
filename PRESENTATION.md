# Swiss Travel Advisor

---

## Application

- Web app with a `/api/chat` endpoint.
- Startup loads destinations, hotels, and activities, then generates embeddings.
- Database stores vectors beside the source data.
- Each question is embedded and searched against the catalog.
- AI provider selects tools; LangChain4j routes calls and messages.

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

## Web application


- `@Controller` exposes the HTTP API.
- `@AiService` creates the assistant implementation.
- Micronaut Data creates repository implementations.
- Compile-time metadata keeps runtime work relatively small and Native Image friendly.

---

## Embeddings in this application

At startup, `DataInitializer` generates embeddings only for catalog rows where
`content_embedding` is missing:

```java
String text = hotel.name()
    + " in " + hotel.destinationName()
    + ". " + hotel.description();

float[] embedding = embeddingService.generateEmbedding(text);
```

- Each embedding is stored in Oracle as a vector of 1,536 numbers.
- Destinations embed name, region, and description; activities also include season.
- At search time, the tool embeds the user's preference with the same service and
  the database ranks matches by cosine distance.

The destination name gives the embedding context. Price and geographic
coordinates remain structured fields for exact filtering.

Code: [DataInitializer.java](src/main/java/com/example/service/DataInitializer.java) · [EmbeddingService.java](src/main/java/com/example/service/EmbeddingService.java)

---

## The assistant is an interface

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
    Double radiusKm,  
    Double maxPrice
) {
    return doSearchNearbyHotels(
        query, nearDestinationName, radiusKm, maxPrice);
}
```

The model never receives database credentials and never writes arbitrary SQL.

Code: [TravelTools.java](src/main/java/com/example/tools/TravelTools.java)

---


## The Java model tells the same story

```java
@MappedEntity("hotels")
public record HotelEntity(
    Long id,
    Double pricePerNight,         
    String description,
    FloatVector contentEmbedding, 
    @Srid(4326) Point location       
) {}
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

From this method name, Micronaut Data generates the Oracle query at compile
time. At runtime, Oracle applies cosine ranking, the geographic radius, and the
price limit, then returns the top five results. No handwritten SQL is needed
for this search.

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

[Combined hotel search](src/main/java/com/example/tools/TravelTools.java#L171)

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

---

## How the expected tool is chosen

`tool_selection` uses a small, demo-specific keyword rule:

- First require a search cue such as `find`, `search`, `recommend`, `suggest`,
  `show`, or `looking for`
- Then use `hotel`, `activity`, or `destination` terms to choose the type of search
- `near`, `around`, `within`, or a supported city → expect the nearby version

For example:

- “Find a hotel near Lucerne” → `searchNearbyHotels`
- “Show activities” → `searchActivities`
- “Save the first hotel to my wishlist” → no search-tool score
- “Hello” → no expected tool and no `tool_selection` score

The rule evaluates the model's choice; it does not control which tool the model calls.

---

## Interpreting the dashboard

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

## Building the native executable

```bash
./mvnw package -Dpackaging=native-image 

./target/swiss-travel-advisor
```

---

## Takeaways

— Use embeddings to capture meaning, and keep factual data queryable.
— Combine semantic relevance with exact business constraints.
- Give the model controlled access through explicit AI-service interfaces and well-defined tool parameters.
-  Use compile-time dependency injection and data access to reduce runtime overhead.
- Give conversation memory and user state explicit, durable boundaries.
- Trace and evaluate model behavior so you can measure and improve it.
-  Use GraalVM Native Image for fast startup, reduced memory footprint, and compact deployment.

---
