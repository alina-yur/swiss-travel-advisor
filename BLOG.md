# Building an AI Travel Assistant with Micronaut and LangChain4j

Nowadays AI provides many great features, from quick answers to smarter search and digital assistants. 

In this post, we'll build a Swiss travel assistant that understands user intent, rather than keywords. When a user asks something like:

> “Provide recommendations for a peaceful mountain resort”,

the application will embed the query, run vector similarity search in the database, and return destinations that mean the right thing — even if they don’t contain the exact words.

We’ll use:

- **Micronaut 4** — lightweight JVM framework with compile-time dependency injection
- **LangChain4j** — a library for LLM orchestration and tool calling
- **Oracle AI Database** — native vector storage and similarity search
- **OpenAI** — our choice of LLM for covering chat and embeddings

---
## Project details

Here’s what we will build:

- A Micronaut web app that exposes a `/api/chat` endpoint  
- On startup, it loads a dataset with travel destinations, hotels, activities
- Each entry gets a vector embedding generated from its description
- Vectors are stored in Oracle AI Database right next to the application data
- At query time, we embed the user question the same way, and run similarity search
- The LLM proposes which tools to call, while LangChain4j handles execution and message routing.


## Semantic search via embeddings

A vector embedding is a numeric representation of text. Similar meanings end up “close” to each other in vector space. So for example “recommend cozy ski town with views” will match destinations described as "calm", "small", and  "scenic". Instead of keyword matching, we compare vector distances and return the closest results. This enables more advanced and rich search user experience for our travel advisor.

---

## Micronaut: fast by design

First, let's briefly look at Micronaut in case you are new to it. 

Micronaut is a JVM framework for building modern lightweight applications. It was introduced as long ago as October 2018 — you can still find Graeme Rocher's [talk](https://www.youtube.com/watch?v=BL9SsY5orkA) presenting it.

The key idea behind Micronaut is shifting dependency injection and annotation processing to compile time instead of runtime. Why? Traditionally, many frameworks heavily relied on reflection to scan application classes and resolve dependencies upon application start. Such approach often ended up being time- and memory consuming. Micronaut moves all of that work to build time - your app starts with everything already wired up. If you are familiar with the key idea behind GraalVM Native Image, you will find this quite similar: shift work to the build time, so you can start instantaneously when it actually matters: at run time.

The performance outcomes of shifting to build time:
- Sub-second startup times on the JVM
- Apps can run with as little as a 10MB heap!
- Less reflection makes native compilation fast and easy out of the box
- On top of that, reduced reflection means better performance and smaller binaries.

When it comes to the most common use cases for Micronaut, microservices, serverless functions, CLI applications, and message-driven services are all great fits, but really, any Java application can benefit from faster startup and lower memory usage.

So the idea behind Micronaut is to provide a **great developer experience and performance at no runtime cost**.

### Core Concepts

Let's look at some of the key concepts of Micronaut.

If you know Spring, many things in Micronaut will look familiar. The programming model is similar - annotations, constructor injection, controllers, and configuration properties work the same way.

#### Compile-Time Dependency Injection

Micronaut uses Java annotation processors to generate bean metadata at compile time. When you annotate a class with `@Singleton` or `@Controller`, the annotation processor generates the injection logic during compilation, with no need for run-time reflection.

As an example, here's what your hello world application could look like:

```java
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;

@Controller("/hello")
public class HelloController {

    @Get
    public String hello() {
        return "Hello, World!";
    }
}
```

As the result, missing dependencies fail at compile time rather than at startup, the generated code is plain Java that compilers can inline and optimize, and initialization time doesn't grow with codebase size.

From annotation processing at compile time comes one distinct quality of Micronaut: when adding Micronaut modules, you often need to include both the library dependency and its corresponding annotation processor. For example, adding LangChain4j support means adding both `micronaut-langchain4j` and `micronaut-langchain4j-processor`. The processor will take care of generating the necessary code.

#### Jakarta Inject Annotations

Micronaut implements the [JSR-330](https://javax-inject.github.io/javax-inject/) dependency injection standard. You can use standard `jakarta.inject` annotations like `@Inject` and `@Singleton`. In addition to familiarity, this makes it easy to write framework-agnostic code and share libraries across projects.

#### Compile-Time Aspect-Oriented Programming

Aspect-oriented programming in Micronaut also happens at compile time. Interceptors are integrated into your code during compilation, avoiding the runtime proxy generation. This means AOP features like `@Cacheable`, `@Transactional`, and custom interceptors work without reflection overhead.

#### GraalVM Native Image Support

Micronaut was designed with GraalVM and AOT compilation in mind. Since all dependency injection and AOP happens at compile time, there's no reflection to configure for native image compilation.

You can compile and run Micronaut applications as native images with the following commands:
```bash
./mvnw package -Dpackaging=native-image
./target/my-application
```

#### Cloud-Native Features

Micronaut was designed from the ground up with cloud environments in mind. The fast startup times and low memory footprint, coming from GraalVM, are complemented nicely by built-in cloud modules and integrations for service discovery, distributed configuration, client-side load balancing, distributed tracing, and major serverless platforms.

#### Polyglot Support

Interestingly, Micronaut is also no stranger to polyglot programming. It supports Java, Kotlin, and Groovy, with features and annotation processing working across the languages.

Micronaut also offers first-class support for Graal Languages, such as GraalPy (`io.micronaut.graal-languages:micronaut-graalpy`), but that's a story for another time.

## LangChain4j: Bringing LLMs to Java

Now let's look at LangChain4j, that will take care of AI orchestration in our project.

LangChain4j is an open-source library that simplifies integrating LLMs into Java applications.

It provides:

-   Chat models (OpenAI, Anthropic, Mistral, etc.)

-   Tool calling (function calling) with structured arguments

-   Chat memory

-   RAG features (embedding models + vector stores)

-   Framework integrations (Micronaut/Spring/Quarkus)


### Core Concepts

Let's look at the key building blocks of LangChain4j.

#### ChatModel

`ChatModel` is the common low-level API for interacting with LLMs, that you are probably familiar with. You build a `ChatRequest` containing `ChatMessage` objects and receive a `ChatResponse` with the model's reply and metadata. The message types include `UserMessage` for user input (which can be multimodal), `SystemMessage` for predefined instructions, and `AiMessage` representing the model's response.

#### AI Services: simple declarative approach

`AI Service` lets you define a Java interface and have LangChain4j generate the implementation:

```java
import dev.langchain4j.service.SystemMessage;
import io.micronaut.langchain4j.annotation.AiService;

@AiService
public interface Assistant {

    @SystemMessage("You are a helpful travel assistant")
    String chat(String userMessage);
}
```

You then inject and call it like any other service, for example `assistant.chat("Find me a hotel in Zurich")`. Under the hood, LangChain4j will create a proxy that converts your method call into the appropriate `UserMessage`, adds the `SystemMessage`, calls the `ChatModel`, and extracts the response text.


AI Services can also handle chat memory, tool execution, structured output parsing, and RAG.

#### Chat Memory

`ChatMemory` automatically manages conversation history, with optional strategies for retaining recent messages.

For persistence beyond in-memory storage, you can implement the `ChatMemoryStore` interface, for example in Oracle Database.


#### Tools

Tools are predefined actions that can be invoked by the LLM. While this doesn't sound too complicated, it's a significant step forward from the early days of using LLMs in applications, when there was little to no determinism and control. Function calling provides structured outputs that match a defined schema, constrains the LLM to actions you've explicitly defined, and separates reasoning from execution, where the LLM decides what to do, but your code controls what actually happens.

```java
@Tool("Search for Swiss destinations matching the query")
public List<Destination> searchDestinations(String query) {
    float[] embedding = embeddingService.embed(query);
    return destinationRepository.searchByVector(embedding, 5);
}
```

When the LLM decides to use a tool, it returns an `AiMessage` containing `ToolExecutionRequest` objects with the tool name and arguments as JSON. The framework then executes the actual Java method, wraps the result in a `ToolExecutionResultMessage`, and sends both messages back to the LLM for the final response.


#### Embedding Models and Stores (RAG)

For Retrieval-Augmented Generation (RAG), LangChain4j provides `EmbeddingModel` and `EmbeddingStore` abstractions.

An `EmbeddingModel` converts text into numerical vectors (embeddings) that capture semantic meaning. 

An `EmbeddingStore` is essentially a vector database interface with methods to add, search, and remove embeddings. During retrieval, user queries are embedded and vector similarity search finds the most relevant content to inject into the prompt.

## Micronaut and LangChain4j in action

Micronaut and LangChain4j together enable a nice declarative approach for extending Java applications with AI capabilities:

```java
@AiService
public interface Assistant {
    @SystemMessage("You are a helpful assistant")
    String chat(@UserMessage String message);
}
```

That can be further injected:

```java
@Inject Assistant assistant;
assistant.chat("What should I see in Zurich?");
```
The implementation will be generated at compile time by `micronaut-langchain4j-processor`.

Similarly, we can create tools for our travel assistant:

```java
@Singleton
public class TravelTools {

    @Tool("""
        Search for Swiss destinations. Use for location queries.
        """)
    public List<Destination> searchDestinations(String query) {
        float[] embedding = embeddingService.embed(query);
        return destinationRepository.searchByVector(embedding, 5);
    }
}
```
 
In this case LangChain4j will handle the function calling protocol with OpenAI: converting the method signature to a JSON schema, parsing the LLM's function call response, invoking the method, and feeding the results back.

## Vector Search with Oracle AI Database

Oracle AI Database supports native vector columns, so you can store embeddings alongside regular relational data.

Simplified, it looks like this:

```sql
CREATE TABLE destinations (
    id NUMBER GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR2(255) NOT NULL,
    region VARCHAR2(255) NOT NULL,
    description CLOB NOT NULL,
    description_embedding VECTOR(1536, FLOAT32)
);
```

The search query uses `VECTOR_DISTANCE` with cosine similarity:

```java
String sql = """
    SELECT id, name, region, description FROM destinations
    WHERE description_embedding IS NOT NULL
    ORDER BY VECTOR_DISTANCE(description_embedding, ?, COSINE)
    FETCH FIRST ? ROWS ONLY
    """;

stmt.setObject(1, queryVector, OracleType.VECTOR);
stmt.setInt(2, limit);
```

What's great about our setup is that the vector operations happen in the database engine, not the application. The database will handle the necessary similarity calculations.

On first startup, the app will automatically generate embeddings for any entries that don't have them yet, after which they will be persisted in the database.

## GraalVM Native Image for performance and efficiency

Micronaut's compile-time approach works great with GraalVM Native Image. Building native executables is straightforward:

```bash
./mvnw package -Dpackaging=native-image
...
./target/swiss-travel-advisor
```

While Micronaut already has a great startup on the JVM, with Native Image it drops down to ~30-50ms. More importantly, memory usage is significantly lower too.


## Bringing everything together

First, let's start our database. You can use Docker or Podman:

```bash
podman run -d -p 1521:1521 --name travel-app-db \
  -e ORACLE_PASSWORD=mypassword \
  -e APP_USER=appuser \
  -e APP_USER_PASSWORD=mypassword \
  gvenzl/oracle-free:latest
```

Once that's ready (and your `OPENAI_API_KEY` is set), start the app:

```bash
export OPENAI_API_KEY=your-key
./target/swiss-travel-advisor
```
This starts the application and populates the database with our predefined data. Flyway runs the migration scripts on startup, creating tables and inserting the destinations, hotels, and activities. Once the server is running, the `DataInitializer` generates vector embeddings, enabling semantic search.

Now for the fun part — let's ask our assistant for travel recommendations. I can highly recommend using [`httpie`](https://github.com/httpie/cli):

```bash
http POST http://localhost:8080/api/chat message="recommend best ski resorts"
```

```
Here are some of the best ski resort destinations in Switzerland for you to consider:

1. St. Moritz (Graubünden)
   - Known for its glamorous vibe, luxury shopping, and winter sports.

2. Zermatt (Valais)
   - Charming alpine village at the foot of the iconic Matterhorn.

3. Interlaken (Bernese Oberland)
   - Adventure capital nestled between Lake Thun and Lake Brienz.

Would you like more information on any of these destinations? Let me know if you'd like to add any to your wishlist!
```

Notice how "best ski resorts" matched destinations based on meaning, not keywords — that's vector search.

Our advisor also supports a wishlist feature. Let's save Interlaken from the suggestions above:

```bash
http POST http://localhost:8080/api/chat message="add Interlaken to a wishlist"
```

```
Interlaken has been added to your wishlist! 🎉 You're going to love the adventure and stunning scenery there. Let me know if you need help with accommodations or activities in the area!
```

And we can retrieve it later:

```bash
http POST http://localhost:8080/api/chat message="retrieve my wishlist"
```

```
Here's your current wishlist:

- **Interlaken (Bernese Oberland)** 🏞️

If you'd like to add more destinations, hotels, or activities, just let me know!✨
```

The LLM decides when to call tools, what parameters to pass, and how to present the results — you just define the tools and rely on Micronaut and Langchain4j for any necessary code generation and other implementation details. Oracle Database handles both the traditional data storage and the vector embeddings, so tools can easily perform semantic searches and persist user data 

## Conclusions

AI-powered assistants are a natural fit for recommendation apps. Users can ask questions in plain language — "recommend a cozy ski town" or "add this to my wishlist" — and the system will do just that.

In this demo, Micronaut offers simplicity, speed, and efficiency, that are great for microservices, assistants, and any applications where speed and memory usage matter. LangChain4j handles the AI orchestration, such as working with chat memory and tool calling. Oracle AI Database stores our vectors alongside application data, so we get easy and powerful similarity search out of the box.

Together, these technologies offer a convenient way to build fast and smart Java applications. You can learn more and find resources to get started at [graalvm.org](https://graalvm.org) and [micronaut.io](https://micronaut.io).
