# Swiss Travel Advisor

An AI-powered travel assistant for discovering Swiss destinations, hotels, and activities. Ask questions in plain language — "recommend a cozy ski town" or "add Zermatt to my wishlist" — and the assistant understands your intent, not just your keywords.

Built with Micronaut 5, LangChain4j, Oracle AI Database, and GraalVM Native Image.

![Micronaut Langchain4j Architecture](assets/micronaut-langchain4j-architecture.png)


## How It Works

When a user asks a question, the app embeds the query using OpenAI's `text-embedding-3-small` model, then uses Micronaut Data repositories to run Oracle AI Database vector similarity search. For location-aware requests, it combines that with Oracle Spatial radius filters over seeded Swiss destination coordinates. The LLM decides which tools to call (search, nearby search, wishlist, etc.), and LangChain4j handles execution and message routing. A bounded chat history is stored in Oracle by conversation ID so follow-up requests retain their context across application restarts.
When a user asks a question, the app embeds the query using OpenAI's `text-embedding-3-small` model, then uses Micronaut Data repositories to run Oracle AI Database vector similarity search. For location-aware requests, it combines that with Oracle Spatial radius filters over seeded Swiss destination coordinates. The LLM decides which tools to call (search, nearby search, wishlist, etc.), and LangChain4j handles execution and message routing. A bounded chat history is stored in Oracle by conversation ID so follow-up requests retain their context across application restarts.

Nearby search resolves location names from the seeded destination catalog, not an external geocoder. The current anchors are Zermatt, Interlaken, Lucerne, Lausanne, St. Moritz, Lugano, and Zurich.

On startup, Flyway runs database migrations and loads destinations, hotels, and activities. The `DataInitializer` then generates and persists vector embeddings for all entries, enabling semantic search from the first request.

## Architecture

- `SwissTravelAssistant` — LangChain4j `@AiService` handling conversation and tool orchestration
- `TravelTools` — `@Tool` methods for semantic search, nearby search, and wishlist management
- Repositories — Micronaut Data JDBC repositories using Oracle vector `Near` queries and Oracle Spatial radius queries
- `EmbeddingService` — generates embeddings via OpenAI
- `DataInitializer` — populates embeddings on startup
- `OracleChatMemoryStore` — persists each conversation's bounded message window as JSON in Oracle
- `OracleChatMemoryStore` — persists each conversation's bounded message window as JSON in Oracle

## Quick Start

### 1. Configure Oracle Database

By default, this project uses [Oracle Autonomous Database](https://www.oracle.com/autonomous-database/) via TLS connection.

Required environment variables:

```bash
export ORACLE_JDBC_URL='<oracle-jdbc-url>'
export DB_PASSWORD=
```

If you already use Micronaut-native datasource variables, these still override the
shared names:

```bash
export DATASOURCES_DEFAULT_URL=
export DATASOURCES_DEFAULT_USERNAME=
export DATASOURCES_DEFAULT_PASSWORD=
```

### 2. Set Your OpenAI API Key

```bash
export OPENAI_API_KEY=your-key
```

### 3. Run the Application

```bash
./mvnw mn:run
```

Flyway runs the migration scripts on startup, creating tables and inserting the
destinations, hotels, and activities. Once the server is running, the
`DataInitializer` generates vector embeddings, enabling semantic search.

Micronaut Test Resources is now disabled by default. If you explicitly want that
old local/dev path, start with:

```bash
./mvnw -Dmicronaut.test.resources.enabled=true mn:run
```

## Building a Native Image

```bash
./mvnw package -Dpackaging=native-image
./target/swiss-travel-advisor
```

The app starts at `http://localhost:8080`.

Open that URL in a browser for the chat interface. It keeps conversation IDs
behind the scenes, lets you reopen previous journeys, and shows the selected
conversation's wishlist in the sidebar. Wishlist items are saved only after an
explicit user request. The JSON API remains available under `/api`.

Open that URL in a browser for the chat interface. It keeps conversation IDs
behind the scenes, lets you reopen previous journeys, and shows the selected
conversation's wishlist in the sidebar. Wishlist items are saved only after an
explicit user request. The JSON API remains available under `/api`.

The native executable:
- Has the size of 132 MB
- Starts and connects to the database in 122 ms
- Even under load, consumes only around 98 MB RAM.


## Example Queries

```bash
http POST http://localhost:8080/api/chat message="recommend best ski resorts"
http POST http://localhost:8080/api/chat message="find quiet lakeside hotels near Lucerne under 250 CHF"
http POST http://localhost:8080/api/chat message="show scenic activities within 40 km of Interlaken"
http POST http://localhost:8080/api/chat message="show best activities in Zurich"
```

Use the same `conversationId` for follow-up requests, including adding or
retrieving items from that conversation's wishlist.

Use the same `conversationId` for follow-up requests, including adding or
retrieving items from that conversation's wishlist.

Or with curl:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "I want to visit a peaceful mountain resort"}'
```

The JSON response contains both `conversationId` and `message`. Pass the same ID
on a follow-up request:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "<conversation-id-from-the-first-response>",
    "message": "Which of those is the most affordable?"
  }'
```

Omit `conversationId` to start a new conversation. Conversation IDs are UUIDs,
and the most recent 20 messages in each conversation are retained.

## Trace LLM Calls with Phoenix

The optional `phoenix` environment traces each OpenAI chat call with
OpenTelemetry and visualizes the complete request/tool/response sequence in
[Arize Phoenix](https://phoenix.arize.com/).

Start the local Phoenix UI and collector:

```bash
podman machine start
podman-compose up -d phoenix
```

Then run the application with tracing enabled:

```bash
MICRONAUT_ENVIRONMENTS=phoenix ./mvnw mn:run
```

Send one of the example chat requests, then open
[http://localhost:6006](http://localhost:6006). Select the
`swiss-travel-advisor` project to inspect model latency, prompts, responses,
dedicated tool spans, JDBC queries, errors, and token usage. The HTTP request,
LLM calls, selected tools, and their database work appear together as one
trace. Phoenix data persists in the `phoenix-data` Podman volume.

The local profile captures prompt and response content so the trace is useful
for debugging. Treat that content as sensitive. Disable capture while retaining
latency, model, status, and token telemetry with:

```bash
AI_TRACING_INCLUDE_CONTENT=false \
  MICRONAUT_ENVIRONMENTS=phoenix ./mvnw mn:run
```

To send traces to another OTLP collector, set `OTEL_EXPORTER_OTLP_ENDPOINT` and,
if needed, `OTEL_EXPORTER_OTLP_PROTOCOL`.

The JSON response contains both `conversationId` and `message`. Pass the same ID
on a follow-up request:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{
    "conversationId": "<conversation-id-from-the-first-response>",
    "message": "Which of those is the most affordable?"
  }'
```

Omit `conversationId` to start a new conversation. Conversation IDs are UUIDs,
and the most recent 20 messages in each conversation are retained.

## Location-Aware Search

This milestone adds location-aware recommendations on top of semantic search.

Micronaut features used:
- Micronaut Data JDBC mapped entities for destinations, hotels, and activities
- Oracle vector search with `FloatVector`, `@VectorIndex`, and repository `Near` methods
- Oracle Spatial locations with `Point`, `@Srid(4326)`, and `SDO_WITHIN_DISTANCE`
- Flyway migrations for `SDO_GEOMETRY` columns, spatial metadata, and spatial indexes
- LangChain4j `@Tool` methods for natural-language tool calls

Example user prompts:

```text
find quiet lakeside hotels near Lucerne under 250 CHF
show scenic activities within 40 km of Interlaken
recommend ski destinations near Zermatt
show best activities in Zurich
```

## TODO

- Add a JSON Trip Plan API using Oracle JSON Relational Duality Views.
- Add an OpenTelemetry demo: keep Hikari, add HTTP + JDBC tracing, custom `TravelTools` spans, native-image verification, and Jaeger/OTLP docs.
- Add embedding-model spans and complete an end-to-end Phoenix UI verification.
