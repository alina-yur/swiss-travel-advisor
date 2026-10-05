# Swiss Travel Advisor

---

Smart travel advisor based on semantic search and grounded in real data.

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

## One user request combines three search criteria

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
- Destinations embed name, region, and description.
- At search time, the tool embeds the user's preference with the same service and the database ranks matches by cosine distance.


Code: [DataInitializer.java](src/main/java/com/example/service/DataInitializer.java) · [EmbeddingService.java](src/main/java/com/example/service/EmbeddingService.java)

---

## Use tools for predefined and constrained actions

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

## From user request to database search

| Component | Responsibility |
|---|---|
| Application | Handles user interaction and passes the user’s input and relevant context to the LLM |
| LLM | Interprets the request, selects a tool, and supplies structured arguments |
| LangChain4j | Maps arguments to Java parameters, invokes the tool, and returns its result to the LLM |
| `TravelTools` | Validates, normalizes, resolves, and converts the arguments |
| Repository | Declares the combined search for Micronaut Data to implement |
| Database | Applies vector, location, and price constraints, then ranks the matches |

---

## The tool prepares database-ready inputs

For "quiet hotel near Lucerne within 15 km under CHF 250", the tool:

- Embeds `"quiet hotel"` as a query vector.
- Resolves `"Lucerne"` to its stored geographic coordinates.
- Normalizes optional values and applies defaults when needed.
- Calls one repository method that combines semantic, spatial, and price criteria.
- Formats the returned hotel entities as text for the LLM.

The LLM interprets the request; the tool performs deterministic preparation and
delegates the search to the database.

---

## One repository method combines the search


[Combined hotel search](src/main/java/com/example/repository/HotelRepository.java#L35)

---

## Database performs the combined search

Conceptually, the generated operation is:

```sql
SELECT ..., VECTOR_DISTANCE(...) AS mn_score
FROM hotels
WHERE VECTOR_DISTANCE(content_embedding, ?, COSINE) <= ?
  AND SDO_WITHIN_DISTANCE(location, ?, 'distance=' || ?) = 'TRUE'
  AND price_per_night <= ?
ORDER BY VECTOR_DISTANCE(content_embedding, ?, COSINE)
FETCH NEXT 5 ROWS ONLY
```

Oracle applies the vector-distance threshold, geographic radius, and price
ceiling, then returns the five best matches ordered by semantic distance. The
SQL is abbreviated; Micronaut Data's generated implementation supplies the
complete dialect-specific statement and parameter mapping.

[Combined hotel search](src/main/java/com/example/repository/HotelRepository.java#L35)

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

## Read one AI trace in Phoenix

Each user message is one `AGENT` trace. Choose a **search request** such as
“Find a quiet lakeside hotel near Lucerne under CHF 250,” then expand
**`tool searchNearbyHotels`**:

```text
AGENT: Swiss Travel Advisor
├── LLM: model chooses a tool
├── TOOL: searchNearbyHotels
│   ├── EMBEDDING: turn the preference into a vector
│   └── RETRIEVER: Oracle vector + spatial search
└── LLM: model writes the answer from the tool result
```


---

## Building the native executable

```bash
./mvnw package -Dpackaging=native-image 

./target/swiss-travel-advisor
```

---

## Takeaways

- Use embeddings to capture meaning, and keep factual data queryable.
- Combine semantic relevance with hard business constraints for valid and useful results.
- Give the model controlled access through well-defined tool parameters.
- Trace and evaluate model behavior so you can measure and improve it.
- Use GraalVM Native Image for fast startup, reduced memory footprint, and compact deployment.

---
