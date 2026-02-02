# Building an AI Travel Assistant with Micronaut and LangChain4j

AI is everywhere — from quick answers to smarter search and “assistant” experiences. So let’s build one.  

In this post, we’ll put together a **Swiss travel assistant** that understands **intent**, not keywords. When a user asks something like:

> “Provide recommendations for a peaceful mountain resort”

…we’ll embed that query, run **vector similarity search** in the database, and return destinations that *mean* the right thing — even if they don’t contain the exact words.

We’ll use:

- **Micronaut 4** — lightweight JVM framework with compile-time DI
- **LangChain4j** — LLM orchestration + tool calling for Java
- **Oracle AI Database (23ai/26ai)** — native vector storage + similarity search
- **OpenAI** — chat + embeddings (GPT-4o, `text-embedding-3-small`)

---

## Project setup 🧳

Here’s what we’re building:

- A Micronaut app that exposes a `/api/chat` endpoint  
- On startup, it loads a small dataset: Swiss destinations, hotels, activities
- Each entry gets a **vector embedding** generated from its description
- Vectors are stored in **Oracle** right next to the application data
- At query time, we embed the user question the same way and run **similarity search**
- The LLM acts as an orchestrator: it decides which **tools** to call and how to present results

---

## Key idea: semantic search with embeddings

A **vector embedding** is a numeric representation of text. Similar meanings end up “close” to each other in vector space.

That’s what enables intent-based search like:

- “Cozy ski town with views” → matches destinations described as calm, alpine, scenic
- “Peaceful mountain resort” → matches entries that talk about wellness, remote valleys, quiet villages  
- “Best ski resorts” → matches well-known Swiss ski areas even if “best” isn’t mentioned anywhere

Instead of keyword matching, we compare vector distances and return the closest results.

---

## Micronaut: fast by design

If you’re new to Micronaut, the elevator pitch is simple:

**Micronaut shifts DI and AOP work to build time.**  
So you avoid runtime reflection-heavy startup work — which is great on the JVM, and *especially* great for native compilation.

### Why it matters

- **Sub-second startup** on the JVM  
- **Low memory footprint** (apps can run on very small heaps)
- **Native Image friendliness** (less reflection, fewer surprises)

Micronaut feels familiar if you know Spring: annotations, controllers, configuration properties, constructor injection — the model is similar.

### Compile-time DI (the core concept)

When you annotate beans like `@Singleton` or `@Controller`, Micronaut generates metadata and wiring at compile time.

A minimal controller looks like this:

```java
@Controller("/hello")
public class HelloController {

    @Get
    public String hello() {
        return "Hello, World!";
    }
}
```

One Micronaut "gotcha" that becomes a feature: when you add framework integrations, you often add **both** the library **and** its annotation processor (because codegen happens at build time). For LangChain4j that typically means:

-   `micronaut-langchain4j`

-   `micronaut-langchain4j-processor`

* * * * *

LangChain4j: AI orchestration for Java
--------------------------------------

LangChain4j makes it practical to build LLM-powered apps in Java without wiring everything by hand.

It gives you:

-   Chat models (OpenAI, Anthropic, Mistral, etc.)

-   Tool calling (function calling) with structured arguments

-   Chat memory

-   RAG building blocks (embedding models + vector stores)

-   Framework integrations (Micronaut/Spring/Quarkus)

### AI Services: the clean, declarative approach

Instead of manually constructing prompts and calling the model, you define an interface:

`@AiService
public interface Assistant {
    @SystemMessage("You are a helpful travel assistant")
    String chat(@UserMessage String message);
}`

Then you inject and call it like a normal service:

`@Inject Assistant assistant;

String reply = assistant.chat("What should I see in Zurich?");`

No boilerplate --- Micronauts processor generates the implementation at compile time.

### Tools: your "actions" the LLM can call

Tools are where the assistant becomes useful and controllable. You define explicit capabilities (search destinations, manage wishlist, etc.), and the LLM decides when to invoke them.

`@Singleton
public class TravelTools {

    @Tool("Search for Swiss destinations matching the query")
    public List<Destination> searchDestinations(String query) {
        float[] embedding = embeddingService.embed(query);
        return destinationRepository.searchByVector(embedding, 5);
    }
}`

Under the hood, LangChain4j models tool calls as ToolExecutionRequests (coming from the AI message) and returns results via ToolExecutionResultMessage.

* * * * *

Vector search with Oracle AI Database
-------------------------------------

Oracle AI Database supports native vector columns, so you can store embeddings alongside normal relational data.

A simplified table looks like this:

`CREATE TABLE destinations (
    id NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR2(200) NOT NULL,
    region VARCHAR2(100),
    description CLOB,
    description_embedding VECTOR(1536, FLOAT32)
);`

Because we’re using `text-embedding-3-small`, our embeddings are 1536-dimensional, so we store them as `VECTOR(1536, FLOAT32`).



And your search query orders by cosine distance:

`String sql = """
    SELECT id, name, region, description
    FROM destinations
    WHERE description_embedding IS NOT NULL
    ORDER BY VECTOR_DISTANCE(description_embedding, ?, COSINE)
    FETCH FIRST ? ROWS ONLY
    """;

stmt.setObject(1, queryVector, OracleType.VECTOR);`

What's nice here: the similarity math happens **in the database engine**, not in your application. No separate vector DB needed, no extra operational layer.

* * * * *

Startup flow: embed once, query fast
------------------------------------

On first run (fresh DB), we generate embeddings for any entries that don't have them yet:

`public class DataInitializer implements ApplicationEventListener<ServerStartupEvent> {

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        // generate embeddings for destinations/hotels/activities
    }
}`

After that, embeddings persist --- so startup stays fast and queries remain efficient.

* * * * *

Native Image support 🚀
-----------------------

Micronaut's compile-time model maps really well to GraalVM Native Image:

`./mvnw package -Dpackaging=native-image
./target/swiss-travel-advisor`

You get:

-   dramatically faster startup (often tens of milliseconds for the app itself)

-   significantly lower memory usage

-   a single self-contained executable

* * * * *

Let's run it
------------

### 1) Start the database (Podman or Docker)

`podman run -d -p 1521:1521 --name travel-app-db\
  -e ORACLE_PASSWORD=mypassword\
  -e APP_USER=appuser\
  -e APP_USER_PASSWORD=mypassword\
  gvenzl/oracle-free:latest`

### 2) Run the app

`export OPENAI_API_KEY=your-key
./mvnw mn:run`

### 3) Query the assistant

`curl -X POST http://localhost:8080/api/chat\
  -H "Content-Type: application/json"\
  -d '{"message": "recommend best ski resorts"}'`

You should see a response that looks like recommendations --- but the key point is *how* it got there:

-   the query was embedded

-   Oracle returned the closest vector matches

-   the LLM turned those into a helpful answer

Wishlist example
----------------

Because it's a travel assistant, it's nice to have a little memory. Let's add something to a wishlist:

`curl -X POST http://localhost:8080/api/chat\
  -H "Content-Type: application/json"\
  -d '{"message": "add Interlaken to a wishlist"}'`

And later, you can fetch it again:

`curl -X POST http://localhost:8080/api/chat\
  -H "Content-Type: application/json"\
  -d '{"message": "retrieve my wishlist"}'`

At a high level, this is the pattern throughout the app: you define the tools (search, add, retrieve), and the model decides when to use them --- then stitches everything into a response that feels natural to the user.

Conclusions
-----------

AI assistants are a natural fit for travel apps because users don't think in filters --- they think in intent.

In this demo:

-   **Micronaut** gives us a fast, lightweight foundation (great for cloud and native)

-   **LangChain4j** makes tool calling + memory + RAG-style retrieval feel clean and "Java-native"

-   **Oracle AI Database** stores vectors right alongside application data and runs similarity search efficiently

Put together, this stack makes building an intelligent assistant surprisingly straightforward --- and very production-shaped.

You can learn more at **micronaut.io** and **graalvm.org**.
