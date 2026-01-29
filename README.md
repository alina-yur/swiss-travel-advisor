# Swiss Travel Advisor

An AI-powered travel assistant for discovering Swiss destinations, hotels, and activities. Built with Micronaut, LangChain4j, Oracle Vector Search, and GraalVM.

## Quick Start

### 1. Start Oracle Database

```bash
podman run -d \
  -p 1521:1521 \
  --name travel-app-db \
  -e ORACLE_PASSWORD=mypassword \
  -e APP_USER=appuser \
  -e APP_USER_PASSWORD=mypassword \
  --shm-size=2g \
  gvenzl/oracle-free:latest
```

Wait for the database to be ready (check with `podman logs -f oracle-free`).

### 2. Set OpenAI API Key

```bash
export OPENAI_API_KEY=
```

### 3. Run the Application

```bash
./mvnw mn:run
```

The application starts at `http://localhost:8080`, runs Flyway migrations, and generates embeddings for all entries.

## Usage

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "I want to visit a peaceful mountain resort"}'
```

## API Endpoints

| Endpoint | Method | Description |
|----------|--------|-------------|
| `/api/chat` | POST | Send a message to the AI assistant |
| `/api/wishlist` | GET | Retrieve saved wishlist items |

## Tech Stack

| Component | Purpose |
|-----------|---------|
| Micronaut 4.10.2 | JVM framework with compile-time DI |
| LangChain4j | LLM integration and tool orchestration |
| Oracle 23ai | Database with native vector search |
| OpenAI | GPT-4o for chat, text-embedding-3-small for embeddings |
| Flyway | Database migrations |
| GraalVM | Native image compilation |

## Architecture

### Core Components

- **SwissTravelAssistant** - LangChain4j `@AiService` handling conversation and tool orchestration
- **TravelTools** - `@Tool` methods for semantic search (destinations, hotels, activities) and wishlist management
- **Repositories** - JDBC-based with Oracle vector distance queries using `VECTOR_DISTANCE(..., COSINE)`
- **EmbeddingService** - Generates embeddings via OpenAI
- **DataInitializer** - Populates embeddings on startup

### Data Model

```sql
destinations (id, name, region, description, description_embedding VECTOR(1536, FLOAT32))
hotels (id, destination_id, name, price_per_night, description, description_embedding VECTOR(1536, FLOAT32))
activities (id, destination_id, name, season, description, description_embedding VECTOR(1536, FLOAT32))
```

**Sample Data:** 6 destinations, 10 hotels, 6 activities

## Building Native Image

```bash
./mvnw package -Dpackaging=native-image
./target/swiss-travel-advisor
```

Expected startup time: ~50-100ms

## Configuration

See [application.properties](src/main/resources/application.properties) for database connection, OpenAI settings, and Flyway configuration.

**Note:** Ensure [pom.xml](pom.xml) includes `flyway-database-oracle` dependency for Oracle 23ai support.
