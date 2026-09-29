# Abstract-to-project feature mapping

With the proposed domain swap from **e-commerce** to **travel discovery**, the abstract maps closely to the current project.

The final column intentionally lists only a specific, defensible advantage of the current Micronaut/Oracle setup over a comparable Spring implementation. `—` means there is no material advantage for that feature. It does **not** mean Spring lacks the feature.

## One-to-one mapping

| Abstract promise | Feature in the current project | Specific Micronaut/Oracle advantage over Spring |
|---|---|---|
| “Turn data into embeddings, store them, and return the closest matches” | Destinations, hotels, and activities store `VECTOR(1536, FLOAT32)` values beside their relational fields. `DataInitializer` generates missing embeddings through LangChain4j, and Micronaut Data `…EmbeddingNear` repository methods return the closest entities. | **Micronaut Data generates and validates these entity-centric vector repository queries during the normal compilation process.** Current Spring Data JPA also supports derived vector searches, but uses its JPA/Hibernate Vector integration; repository AOT generation is a separate AOT optimization. |
| “Application data, business logic, and practical constraints” | Similarity searches combine destination IDs, maximum hotel price, activity season data, geographic radius, supported location anchors, conversation ownership, and explicit wishlist consent. | **Relational and vector predicates can be expressed together in compile-time-validated Micronaut Data JDBC repository methods without adding a JPA provider or separate vector-store abstraction.** Spring can implement the same rules. |
| “Building an e-commerce application” | The current project is a travel discovery catalog with prices, recommendations, and conversation-scoped wishlists, but no booking or checkout transaction. | — |
| “Building with Micronaut, LangChain4j, and GraalVM” | Micronaut supplies HTTP endpoints, dependency injection, configuration, serialization, Data JDBC, Flyway integration, and the Native Image build. Micronaut LangChain4j supplies model configuration and declarative AI-service integration. | **Micronaut uses compile-time dependency-injection and serialization metadata in ordinary builds, so the application is already organized around closed-world-friendly metadata before Native Image packaging is selected.** Spring supports Native Image through a dedicated Spring AOT transformation. |
| “The strategy for generating vector embeddings” | Embedding text includes business context: destination name/region/description, hotel name/destination/description, and activity name/destination/season/description. Startup processes only records whose embedding is null. | — |
| “Combining similarity results with application data and business constraints” | Hotel repository methods combine vector distance with destination and price. Activity methods combine vector distance with destination. Tool logic normalizes optional filters, validates wishlist references, and scopes saved items by conversation. | **Micronaut Data validates the derived vector-plus-relational repository signatures at compilation time in the standard build and maps Oracle vector values directly over JDBC.** Spring Data can express equivalent predicates, but its JPA route requires Hibernate Vector and associated entity mapping. |
| “Combining semantic and location-aware search” | One Oracle SQL statement applies `SDO_WITHIN_DISTANCE`, optionally applies a maximum hotel price, orders the surviving rows by cosine `VECTOR_DISTANCE`, and returns the top five. This exists for destinations, hotels, and activities. | **The current Micronaut Data stack provides documented Oracle dialect support for both vector values/search and `SDO_GEOMETRY` predicates, with a straightforward JDBC escape hatch for composing both in one exact Oracle query.** The database performs semantic, spatial, and business filtering without moving intermediate results into the application. Spring with Oracle JDBC can execute the same SQL, so the database operation itself is not Micronaut-exclusive. |
| “Most helpful features of Micronaut Data” | Generated repositories handle ordinary CRUD and derived vector/business queries. Custom JDBC handles the combined Oracle Spatial/vector query where exact SQL is clearer. | **This is the clearest framework advantage: Micronaut Data pre-computes repository queries, checks whether methods can be implemented at compilation time, and does not use runtime repository proxies as its default architecture.** Spring Data 4.x offers AOT repositories, but they are an AOT optimization rather than its sole/default execution model. |
| “Most helpful features of Micronaut LangChain4j” | `@AiService`, `@SystemMessage`, `@MemoryId`, `@UserMessage`, `@Tool`, and `@ToolMemoryId` provide declarative conversation and tool orchestration. Tool beans use constructor injection, and chat history is stored in Oracle. | — |
| “Building and running the application as a Native Image” | The Maven build uses GraalVM Native Build Tools, includes reachability metadata, documents native packaging, and provides a script that launches the executable against local Oracle. | **Micronaut's DI, HTTP, serialization, and Data layers already use build-time metadata in the regular programming model, reducing the conceptual gap between normal and Native Image builds.** Spring Boot also has first-class Native Image support, but applies Spring AOT and its restrictions as a dedicated build mode. |
| “Turn basic similarity search into a more intelligent and useful application” | The model selects specialized tools; retrieval combines semantics, geography, price, entity type, season, conversation memory, and user-controlled wishlist mutations. Phoenix exposes agent, model, tool, embedding, and retrieval spans. | — |

## Advantages worth emphasizing in the session

### 1. Compile-time data access is the strongest Micronaut distinction

Micronaut Data does not wait until application startup to interpret repository method names. It generates query implementations and validates supported repository signatures during compilation. This applies to the project's ordinary relational filters and Oracle vector searches in the normal build—not only when creating a Native Image executable.

Spring Data 4.x now supports vector repository methods and AOT-generated repositories, so the accurate claim is **“compile-time by default in Micronaut Data,”** not **“unavailable in Spring.”**

### 2. Oracle vectors remain part of the business data model

The application does not copy destinations, hotels, or activities into a separate generic document store. Each Oracle row contains its business attributes, location, and embedding. That makes queries such as “quiet lakeside hotels near Lucerne under CHF 250” a single database operation over authoritative application data.

Spring can use the same database design. The convenience here is the combination of Micronaut Data JDBC's direct entity mapping, Oracle vector support, derived business predicates, and custom JDBC in one lightweight toolkit.

### 3. Oracle performs semantic, spatial, and business filtering together

The nearby hotel query combines:

1. `price_per_night <= ?`
2. `SDO_WITHIN_DISTANCE(...)`
3. `ORDER BY VECTOR_DISTANCE(..., COSINE)`
4. `FETCH FIRST 5 ROWS ONLY`

This avoids retrieving a semantic candidate set and filtering it in application memory. It is primarily an **Oracle AI Database advantage**. Micronaut makes it convenient to integrate, but a Spring application using Oracle JDBC can run the same query.

### 4. The ordinary Micronaut model is already Native Image-oriented

Micronaut generates dependency-injection, serialization, HTTP, and Data metadata at build time as part of its usual programming model. The Native Image build can reuse that design directly.

Spring Boot also supports Native Image well, but it performs a dedicated Spring AOT pass that inspects the application context and generates source, proxy, and runtime-hint assets. Therefore, emphasize Micronaut's **consistent compile-time model**, not exclusive Native Image support.

## Features that are not Micronaut advantages over Spring

- LangChain4j `@AiService`
- LangChain4j tools and chat memory
- OpenAI chat and embedding models
- Derived vector search methods in current Spring Data
- Oracle vector search
- Custom Oracle Spatial/vector SQL
- Flyway migrations
- REST APIs and dependency injection
- Observability
- GraalVM Native Image support

These remain important project features, but modern Spring applications have equivalents.

## Oracle Database features—not Micronaut features

- `VECTOR(1536, FLOAT32)` storage
- `VECTOR_DISTANCE(..., COSINE)` ranking
- `SDO_GEOMETRY` and spatial indexes
- `SDO_WITHIN_DISTANCE` radius filtering
- Combining relational, spatial, and vector operations in one SQL query

## Key implementation evidence

- Embedding strategy: `src/main/java/com/example/service/DataInitializer.java`
- LangChain4j embedding model: `src/main/java/com/example/service/EmbeddingService.java`
- Business filters and AI tools: `src/main/java/com/example/tools/TravelTools.java`
- Combined semantic, spatial, and price SQL: `src/main/java/com/example/repository/SpatialSearchRepository.java`
- AI service and tool-selection policy: `src/main/java/com/example/service/SwissTravelAssistant.java`
- Vector schema: `src/main/resources/db/migration/V1__create_schema.sql`
- Spatial schema and indexes: `src/main/resources/db/migration/V3__add_location_search.sql`
- Native Image configuration: `pom.xml`
- Demonstration instructions: `README.md`


## Claims to qualify before presenting

1. The current embedding backfill detects missing embeddings but not changed source text or a changed embedding model. A content hash and model-version marker would make the strategy more production-complete.
2. The test suite passes but remains small. A fresh Native Image build plus database-backed semantic and spatial smoke tests should be part of conference preflight.
3. Do not claim that vector search, LangChain4j AI services, tools, Oracle integration, or Native Image are unavailable in Spring. The defensible distinction is Micronaut's default compile-time model and the convenience of Micronaut Data's Oracle vector/spatial integration.
