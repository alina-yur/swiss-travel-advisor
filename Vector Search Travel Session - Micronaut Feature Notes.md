# Vector Search Travel Session — Micronaut Feature Notes

Verified against public Micronaut documentation on 2026-09-21. The internal presentation was used only to identify candidate topics; release status comes from the public sources linked below.

### Version-line clarification

Micronaut modules have independent versions. As of the verification date:

- **Micronaut Core 5.2.3 is released.**
- **Micronaut Platform 5.1.5 is the latest released platform BOM.** The Platform 5.2 documentation is still labeled `5.2.0-SNAPSHOT`.
- **Micronaut Data 5.1.4 is the latest released Data version.** The Data 5.2 APIs discussed below are still published as `5.2.0-SNAPSHOT`.

Therefore, the existence of Micronaut Core 5.2.x does not make Micronaut Data 5.2 features released. Feature availability must be checked against the owning module, not Core's version number.

## Recommended session thesis

The most useful framing is not “how to call an embedding model.” It is:

> Semantic similarity finds plausible candidates; the application turns those candidates into valid recommendations.

The travel example makes that distinction concrete. A user may ask for “a quiet hotel near the old town with late check-in,” but a useful result must also satisfy location, dates, inventory, price, accessibility, policy, and authorization constraints. Micronaut can provide the typed application and data layer around the probabilistic search step.

## Suggested narrative

### 1. Start with the deliberately incomplete prototype

Show the smallest possible path:

1. Turn a free-text request into an embedding.
2. Store destination embeddings.
3. Retrieve the nearest results.

Then show why this is insufficient:

- The closest semantic match may be too far away.
- The property may be unavailable for the requested dates.
- Price and inventory become stale quickly and do not belong in a long-lived embedding.
- Similarity scores are not business scores.
- An attractive answer is still wrong if it invents availability or policies.

This creates the reason for every Micronaut feature that follows.

### 2. Treat embeddings as one part of the domain model

A useful travel-search record might contain:

- Stable descriptive text and its embedding.
- A geographic point with an explicit SRID.
- Structured attributes such as category, amenities, accessibility, and tenant.
- Volatile facts such as price and availability in their authoritative tables or services.
- Embedding metadata: model name/version, dimensions, source hash, language, and generation time.

Speaker point: do not embed facts merely because they can be embedded. Keep exact and frequently changing facts structured so they remain filterable and trustworthy.

### 3. Explain the embedding strategy

Questions worth making explicit:

- **What is embedded?** Prefer stable descriptive content: name, description, destination context, amenity labels, and carefully selected review summaries.
- **What is not embedded?** Current price, room inventory, user entitlements, and other fast-changing constraints.
- **When is it generated?** Usually on ingestion or after a relevant content change; the query embedding is generated at request time.
- **How is it versioned?** Store the model/version and an input hash so re-embedding can be incremental and auditable.
- **How is a model migration handled?** Keep old and new embeddings side by side, backfill, compare retrieval quality, then switch traffic.
- **How are dimensions enforced?** Make the vector dimension part of the schema and validate it before persistence.

### 4. Add structured and business constraints

Present three implementation shapes and explain the trade-off:

1. **Filter, then rank:** apply hard constraints first, then vector-rank the remaining rows. Strong correctness, but restrictive filters can leave too few candidates.
2. **Retrieve, then filter:** retrieve a larger semantic candidate set and apply business rules afterward. Simple, but can lose valid results if the initial candidate window is too small.
3. **Hybrid query:** combine vector distance, spatial predicates, and structured filters in one database query, then optionally re-rank in the application. This is the strongest demonstration when the database supports both vector and spatial operations.

Micronaut Data derived methods are useful for the simple pieces. For a combined score or vendor-specific hybrid statement, use an explicit `@Query` and return a projection containing the entity plus score/distance fields.

### 5. Make semantic plus location-aware search the centerpiece

The geospatial material from the presentation fits the travel story especially well:

- Map a destination or property location to Micronaut Data geometry types.
- Use `@Srid` to make coordinate-system assumptions explicit.
- Use `GeoWithin` for a neighborhood or map polygon.
- Use `GeoIntersects` for a route or corridor.
- Use `Near` for “within N units of this point.”
- On Oracle, Micronaut Data maps these operations to `SDO_GEOMETRY` and spatial predicates.

A strong demo progression is:

```text
“quiet boutique hotel”
    → semantic candidates
    → within the requested map area
    → available for the requested dates
    → within budget
    → ranked response with reasons
```

Important caveat from the public documentation: Oracle `SDO_UTIL.FROM_GEOJSON` currently has a documented issue with `NULL`; WKT is the documented fallback for nullable geospatial columns.

### 6. Use Micronaut LangChain4j at the application boundary

Features worth mentioning:

- **`@AiService`:** define a typed interface and inject it like another Micronaut component. This keeps prompts and model interaction behind an application-level contract.
- **Model configuration:** supported model integrations are configured as Micronaut beans, making model choice and configuration external to business code.
- **Embedding-store integrations:** the released integration documents in-memory, Oracle, PGVector, Chroma, Elasticsearch, MongoDB, Neo4j, OpenSearch, Redis, and Qdrant stores.
- **Tools:** expose authoritative operations—availability, pricing, policy lookup, booking rules—as injected tool beans. The model can request an operation, while application code still owns validation and authorization.
- **Response streaming:** useful for perceived latency in an interactive itinerary or recommendation UI.
- **Evaluation API:** `RelevancyEvaluator` and `FactCheckingEvaluator` make a good bridge from a demo to repeatable quality checks. Retrieved sources can be reused as grounding context.
- **Guardrails and agentic services:** available in the released 2.2.0 line, but they are optional for this story. A simple retrieval pipeline is easier to explain than a multi-agent design.

Micronaut LangChain4j 2.2.0 is released, but its own guide still labels the integration **experimental and subject to change**. Say both things: released does not mean the API has left experimental status.

### 7. Show how Micronaut Data reduces glue code

The most relevant broader Micronaut Data points are:

- Compile-time repository implementation and query validation.
- JDBC and R2DBC support.
- SQL vector types and derived vector-search methods.
- Explicit scoring functions and result objects containing raw score and normalized similarity when available.
- Derived queries for ordinary filters.
- DTO projections so search endpoints fetch only the fields they return.
- Pagination and top-N methods to bound candidate sets.
- Optimistic locking for inventory or booking state.
- Explicit `@Query` support for the final hybrid query.

The key message is not merely “less boilerplate.” Compile-time query generation and validation move failures earlier and reduce runtime machinery, which also supports the final Native Image part of the session.

### 8. Make testing part of the search story

Micronaut Test Resources can provision database dependencies during development and tests. Public documentation includes Oracle Database Free/XE and PostgreSQL support, while the Micronaut LangChain4j guide shows test-resource integrations for Ollama and Qdrant.

Useful tests to mention or demonstrate:

- Repository integration tests against the actual vector/spatial database rather than an in-memory substitute.
- A small golden query set with expected destinations or required constraints.
- Assertions that hard constraints are never violated.
- Retrieval metrics such as recall at K before testing generated prose.
- Relevancy and fact-grounding evaluations for the final response.
- A smoke test of the packaged native executable, including TLS, model-client, database-driver, serialization, and environment configuration paths.

### 9. Finish with GraalVM Native Image

The payoff is a normal Micronaut application assembled as a native executable, not a separate architecture for AI workloads.

Points to show:

- Micronaut’s build plugins expose `nativeCompile` and native test tasks.
- Micronaut’s compile-time dependency injection, repositories, and serialization reduce reliance on runtime reflection.
- Micronaut Data publicly documents Native Image support for JDBC and JPA, including Oracle and PostgreSQL drivers.
- Micronaut Serialization uses build-time bean introspection and is a natural choice for request/response models.
- Measure startup and memory rather than claiming improvements without numbers from the demo environment.

Important caveat: the public Micronaut LangChain4j guide does not make a blanket Native Image compatibility promise for every model provider and transitive SDK. Verify the exact provider, HTTP client, database driver, and serialization path used by the demo with a native build and end-to-end smoke test.

Typical Gradle build step:

```shell
./gradlew nativeCompile
```

## Features from the internal presentation

| Feature | Public status | Relevance to this session | Recommendation |
|---|---|---|---|
| Geospatial persistence and predicates | **Released** in Micronaut Data 5.0; documented in the stable 5.1.x guide | Directly enables semantic plus location-aware travel search | Make this a core part of the demo |
| Oracle `SDO_GEOMETRY`, `@Srid`, GeoJSON/WKT conversion | **Released** and documented in the stable guide | Shows that location is modeled, indexed, and queried rather than delegated to the model | Mention with the geospatial query |
| Oracle `INTERVAL` mapping to `Duration` and `Period` | **Released since 5.0** and documented in stable 5.1.x | Could model trip duration, transfer windows, or cancellation periods | Mention only if the domain model genuinely uses intervals |
| Generic repository `@Upsert` with `conflictsOn` | **Not in the released Data 5.1.4 line**; public API is currently `5.2.0-SNAPSHOT` | Would be useful for idempotent destination/embedding ingestion | Keep out of the main demo; optionally identify it as an upcoming capability |
| Oracle sessionless transactions | **Not in the released Data 5.1.4 line**; public API is currently `5.2.0-SNAPSHOT` | Potentially interesting for booking work that crosses request/session boundaries, but not for search itself | Omit unless there is a clearly labeled future-looking section |

## Additional released features worth highlighting

| Feature | Verified public line | Why it helps the session |
|---|---|---|
| SQL vector type mapping | Micronaut Data in Micronaut Framework 5.0.0 | Lets the domain model persist vectors without treating them as opaque strings |
| Derived vector search and scoring functions | Stable Micronaut Data 5.1.x documentation | Provides repository methods for `Near`, `Within`, `Between`, top-N ordering, `Score`, `Similarity`, and dialect-specific scoring functions |
| Oracle dense and sparse vector support | Stable Micronaut Data 5.1.x documentation | Relevant if the travel application uses Oracle for both semantic and spatial data |
| `@AiService` and tool beans | Micronaut LangChain4j 2.2.0 | Keeps model interaction typed and lets authoritative business operations remain ordinary injected services |
| Oracle and PGVector embedding stores | Micronaut LangChain4j 2.2.0 | Gives the session a choice between LangChain4j’s embedding-store abstraction and direct Micronaut Data vector repositories |
| Response streaming | Micronaut LangChain4j 2.2.0 | Improves the interactive experience without changing retrieval correctness |
| AI-response evaluation API | Micronaut LangChain4j 2.2.0 | Turns relevance and grounding into testable concerns |
| Agentic services and guardrail integration | Micronaut LangChain4j 2.2.0, released with Micronaut Framework 5.1.0 | Interesting as an optional extension; not necessary for the core retrieval story |
| Test Resources | Stable public documentation | Makes real database and local model dependencies reproducible for development and tests |
| Micronaut Serialization | Stable public documentation | Build-time introspection and a smaller reflection footprint fit the Native Image conclusion |
| Native build and test tasks | Stable Micronaut Gradle plugin documentation | Provides a concrete final build step and a way to verify the actual dependency combination |

## Recommended emphasis

### Must mention

- Embedding lifecycle and versioning.
- Micronaut Data vector mapping/search.
- Structured business constraints.
- Released geospatial support for hybrid semantic/location search.
- Typed `@AiService` integration.
- Native build plus an honest compatibility verification step.

### Good supporting material

- Tools for live availability and pricing.
- DTO projections and pagination.
- Test Resources with the real database.
- Relevancy and fact-grounding evaluations.
- Response streaming.

### Probably omit from the main flow

- Interval mapping, unless it appears naturally in the domain.
- Agentic orchestration, unless the session specifically contrasts deterministic pipelines with agents.
- Sessionless transactions and generic upserts, because the public stable line has not released the APIs shown in the internal presentation.

## Possible closing summary

> Embeddings provide a useful signal, not a complete answer. Micronaut Data can combine that signal with vector-aware repositories, spatial predicates, and ordinary business data. Micronaut LangChain4j provides typed model integration, tools, and evaluation. The same application can then be tested against real infrastructure and built as a GraalVM native executable.

## Public verification sources

- [Micronaut Framework 5.0.0 release](https://micronaut.io/2026/05/20/micronaut-framework-5-0-0-released/) — announces Micronaut Data geospatial support and SQL vector type mapping.
- [Micronaut Framework 5.1.0 release](https://micronaut.io/2026/07/27/micronaut-framework-5-1-0-release/) — includes Micronaut LangChain4j 2.2.0 and lists agentic support, guardrails, evaluation testing, Chroma, and Oracle chat-memory configuration.
- [Micronaut Core releases](https://github.com/micronaut-projects/micronaut-core/releases) — shows Core 5.2.3 as released on 2026-09-21.
- [Micronaut Platform releases](https://github.com/micronaut-projects/micronaut-platform/releases) — shows Platform 5.1.5 as the latest released platform line.
- [Micronaut Platform snapshot guide](https://micronaut-projects.github.io/micronaut-platform/snapshot/guide/) — identifies the next platform line as `5.2.0-SNAPSHOT`.
- [Micronaut Data 5.1.4 release](https://github.com/micronaut-projects/micronaut-data/releases/tag/v5.1.4) — latest released Data version at the verification date.
- [Micronaut Data 5.1.2 guide](https://micronaut-projects.github.io/micronaut-data/5.1.2/guide/) — stable 5.1.x documentation inspected for vector search, geospatial support, Oracle interval mapping, repositories, projections, pagination, optimistic locking, and Native Image support.
- [Micronaut Data 5.2 snapshot `@Upsert` API](https://micronaut-projects.github.io/micronaut-data/snapshot/api/io/micronaut/data/annotation/Upsert.html) — identifies the generic upsert API as `5.2.0-SNAPSHOT`/since 5.2.0.
- [Micronaut Data 5.2 snapshot sessionless API](https://micronaut-projects.github.io/micronaut-data/snapshot/api/io/micronaut/transaction/sessionless/SessionlessTransactionHandler.html) — identifies sessionless transaction infrastructure as snapshot/since 5.2.
- [Micronaut LangChain4j 2.2.0 guide](https://micronaut-projects.github.io/micronaut-langchain4j/2.2.0/guide/) — released guide for `@AiService`, tools, streaming, evaluations, agents, supported models, and embedding stores; it also states that the integration remains experimental.
- [Micronaut Test Resources guide](https://micronaut-projects.github.io/micronaut-test-resources/latest/guide/) — stable documentation for automatically managed development and test dependencies.
- [Micronaut Serialization documentation](https://docs.micronaut.io/5.0.x/serde/) — documents build-time, reflection-free serialization.
- [Micronaut Gradle plugin documentation](https://micronaut-projects.github.io/micronaut-gradle-plugin/latest/) — documents Native Image and AOT build tasks, native testing, and Test Resources integration.
- [Micronaut GraalVM reflection guide](https://guides.micronaut.io/latest/micronaut-graalvm-reflection.html) — explains reflection metadata requirements for Native Image.
