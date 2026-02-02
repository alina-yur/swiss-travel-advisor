# Building an AI Travel Assistant with Micronaut and LangChain4j

Nowadays AI provides many great features, from quick answers to smarter search and digital assistants. 

In this post, we’ll put together a **Swiss travel assistant** that understands **user intent**, not keywords. When a user asks something like:

> “Provide recommendations for a peaceful mountain resort”

... we’ll embed that query, run **vector similarity search** in the database, and return destinations that *mean* the right thing — even if they don’t contain the exact words.

We’ll use:

- **Micronaut 4** — lightweight JVM framework with compile-time dependency injection
- **LangChain4j** — LLM orchestration and tool calling
- **Oracle AI Database** — native vector storage and similarity search
- **OpenAI** — chat and embeddings (GPT-4o, `text-embedding-3-small`)

---

## Semantic search via embeddings

A **vector embedding** is a numeric representation of text. Similar meanings end up “close” to each other in vector space.

That’s what enables intent-based search like:

- “Cozy ski town with views” → matches destinations described as calm, small, scenic
- “Peaceful mountain resort” → matches entries that mention wellness, peace, quiet villages  
- “Best ski resorts” → matches well-known Swiss ski areas.

Instead of keyword matching, we compare vector distances and return the closest results.

---

## Micronaut: fast by design

First, let's briefly look at Micronaut in case you are new to it. 

Micronaut is a JVM framework for building modern lightweight applications. It was introduced as long ago as October 2018 — you can still find Graeme Rocher's [talk](https://www.youtube.com/watch?v=BL9SsY5orkA) presenting it.

The key idea behind Micronaut is shifting dependency injection and annotation processing to compile time instead of runtime. Wondering why? Traditionally, frameworks used to heavily rely on reflection to scan application classes and wire up dependencies upon application start. Such approach often ended up being time- and memory consuming. Micronaut moves all of that to build time - your app starts with everything already wired up. If you are familiar with the key idea behind GraalVM Native Image, you will find this quite similar: shift work to the build time, so you can start instantaneously when it actually matters: at run time.

The performance outcomes of shifting to build time:
- Sub-second startup times on the JVM
- Apps can run with as little as a 10MB heap!
- Less reflection make native compilation fast and easy out of the box
- On top of that, reduced reflection means better performance and smaller binaries.

When it comes to the best use cases for Micronaut, microservices, serverless functions, CLI applications, and message-driven services are all great fits, but really, any Java application can benefit from faster startup and lower memory usage.

So the idea behind Micronaut is to provide a **great developer experience and performance at no runtime cost**.

## Core Concepts

Let's look at some of the key concepts of Micronaut.

If you know Spring, many things in Micronaut will look familiar. The programming model is similar - annotations, constructor injection, controller patterns, and configuration properties work the same way.

#### Compile-Time Dependency Injection

Micronaut uses Java annotation processors to generate bean metadata at compile time. When you annotate a class with `@Singleton` or `@Controller`, the annotation processor generates the injection logic during compilation, with no need for run-time reflection.

As an example, here's what your hello world application could look like:

```java
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

Micronaut implements the [JSR-330](https://javax-inject.github.io/javax-inject/) dependency injection standard. You can use standard `jakarta.inject` annotations like `@Inject` and `@Singleton`. This makes it easy to write framework-agnostic code and share libraries across projects.

#### Compile-Time Aspect-Oriented Programming

Aspect-oriented programming in Micronaut also happens at compile time. Interceptors are integrated into your code during compilation, avoiding the runtime proxy generation that other frameworks use. This means AOP features like `@Cacheable`, `@Transactional`, and custom interceptors work without the reflection overhead.

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

## LangChain4j

Now let's look at LangChain4j, that will take care of AI orchestration in our project.

LangChain4j is an open-source library that simplifies integrating LLMs into Java applications. The project was started back in early 2023 by Dmytro Liubarskyi, who discovered that at the time there was no convenient way to build LLM-powered chatbots in Java. 

The framework provides unified APIs for working with proprietary APIs from 20+ LLM providers, such as OpenAI, Anthropic, Mistral, and others, and 30+ vector stores, such as Oracle, behind common interfaces. This means you can switch providers without rewriting application logic.

### Core Concepts

Let's look at the key building blocks of LangChain4j.

#### ChatModel

`ChatModel` is the low-level API for interacting with LLMs, that you are likely familiar with. It accepts `ChatMessage` objects and returns a `ChatResponse`. You are probably also familiar with `UserMessage`,  representing user input (optionally multimodal), `SystemMessage`, which is developer-defined instructions describing the model's role and behavior, and `AiMessage`, representing the model's response.

#### AI Services

`AI Services` provide a declarative approach: instead of manually calling `ChatModel`, constructing messages, and parsing responses, AI Services let you define a Java interface and have LangChain4j generate the implementation:

```java
import dev.langchain4j.service.SystemMessage;
import io.micronaut.langchain4j.annotation.AiService;

@AiService
public interface Assistant {

    @SystemMessage("You are a helpful travel assistant")
    String chat(String userMessage);
}
```

You then inject and call it like any other service — `assistant.chat("Find me a hotel in Zurich")`. Under the hood, LangChain4j will create a proxy that converts your method call into the appropriate `UserMessage`, adds the `SystemMessage`, calls the `ChatModel`, and extracts the response text.


AI Services can also handle chat memory, tool execution, structured output parsing (returning POJOs instead of strings), and RAG — all configured through the builder or annotations.

#### Chat Memory

`ChatMemory` manages conversation history automatically, with built-in strategies for retaining recent messages either by count,  or by token limit.

For persistence beyond in-memory storage, you can implement the `ChatMemoryStore` interface, like we will do in our project.


#### Tools

Tools are predefined actions that can be invoked by the LLM. While this doesn't sound too complicated, it's a significant step forward from the early days of using LLMs in applications, when there was little to no determinism and control. Function calling provides structured outputs that match a defined schema, constrains the LLM to actions you've explicitly defined, and separates reasoning from execution - the LLM decides what to do, but your code controls what actually happens.

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

An `EmbeddingModel` converts text into numerical vectors (embeddings) that capture semantic meaning. LangChain4j even includes some models that run locally via the [ONNX Runtime](https://onnxruntime.ai/), so you can generate embeddings without external API calls.

An `EmbeddingStore` is essentially a vector database interface with methods to add, search, and remove embeddings. During retrieval, user queries are embedded and vector similarity search finds the most relevant content to inject into the prompt.


### Framework Integrations

LangChain4j integrates with major Java frameworks. In our project, we're using the Micronaut integration where the `@AiService` annotation triggers compile-time code generation — no runtime reflection needed.


## Project Overview

This project is a travel advisor application, that lets users ask natural language questions about travel options in Switzerland and get relevant recommendations from a database of destinations, hotels, and activities. Instead of exact keyword matching, it converts queries into vector embeddings and finds the most similar entries using cosine distance.

The stack:
- Micronaut 4 - web framework
- LangChain4j - LLM orchestration with function calling
- Oracle 23ai - database with vector support
- OpenAI - `GPT-4o` for chat, `text-embedding-3-small` for embeddings

## Micronaut in action

LangChain4j and Micronaut together enable a nice declarative approach to AI integration:

```java
@AiService
public interface Assistant {
    @SystemMessage("You are a helpful assistant")
    String chat(@UserMessage String message);
}
```

That cab be further injected:

```java
@Inject Assistant assistant;
assistant.chat("What should I see in Zurich?");
```

No boilerplate, no manual wiring. The annotation processor generates the implementation at compile time.


At compile time, `micronaut-langchain4j-processor` will generate the implementation.

Same goes for the tools the AI can call:

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
 
The framework handles the function calling protocol with OpenAI - converting the method signature to a JSON schema, parsing the LLM's function call response, invoking the method, and feeding results back.

## Vector Search with Oracle

Oracle 23ai has native vector column support. Each travel entry stores its description plus a 1536-dimensional embedding:

```sql
CREATE TABLE destinations (
    id NUMBER GENERATED BY DEFAULT AS IDENTITY PRIMARY KEY,
    name VARCHAR2(200) NOT NULL,
    region VARCHAR2(100),
    description CLOB,
    description_embedding VECTOR(1536, FLOAT32)
);
```

The search query uses `VECTOR_DISTANCE` with cosine similarity:

```java
String sql = """
    SELECT id, name, region, description
    FROM destinations
    WHERE description_embedding IS NOT NULL
    ORDER BY VECTOR_DISTANCE(description_embedding, ?, COSINE)
    FETCH FIRST ? ROWS ONLY
    """;
stmt.setObject(1, queryVector, OracleType.VECTOR);
```

What's pretty cool: the vector operations happen in the database engine, not your application. Oracle handles the similarity math at query time.

## Startup Flow

On startup, the app generates embeddings for all entries that don't have them yet:

```java
public class DataInitializer implements ApplicationEventListener<ServerStartupEvent> {

    @Override
    public void onApplicationEvent(ServerStartupEvent event) {
        ... Generate embeddings for destinations, hotels, activities
    }
}
```

This runs once when you first start the app with a fresh database. After that, the embeddings persist and startup is fast.

## Native Image Support

Micronaut's compile-time approach works great with GraalVM Native Image. Building native executables is straightforward:

```bash
./mvnw package -Dpackaging=native-image
...
./target/swiss-travel-advisor
```

While Micronaut already has a great startup on the JVM, with Native Image it drops down to ~30-50ms. More importantly, Memory usage is significantly lower too.


The LLM acts as an orchestrator. It interprets user intent, decides which tools to call (and with what parameters), and synthesizes the results into a response. You write the tools, the framework handles the plumbing.

## Let's Run It

First, let's get our database. You can use Docker or Podman:

```bash
podman run -d -p 1521:1521 --name travel-app-db \
  -e ORACLE_PASSWORD=mypassword \
  -e APP_USER=appuser \
  -e APP_USER_PASSWORD=mypassword \
  gvenzl/oracle-free:latest
```

Once that's ready, set your OpenAI key and start the app:

```bash
export OPENAI_API_KEY=your-key
./mvnw mn:run
```

Now for the fun part — let's ask our assistant for travel recommendations:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "recommend best ski resorts"}'
```

```
Here are some of the best ski resort destinations in Switzerland for you to consider:

1. **St. Moritz (Graubünden)**
   - Known for its glamorous vibe, luxury shopping, and winter sports.

2. **Zermatt (Valais)**
   - Charming alpine village at the foot of the iconic Matterhorn.

3. **Interlaken (Bernese Oberland)**
   - Adventure capital nestled between Lake Thun and Lake Brienz.

Would you like more information on any of these destinations? Let me know if you'd like to add any to your wishlist! 🌟
```

Notice how "best ski resorts" matched destinations based on meaning, not keywords. That's vector search at work.

The assistant also supports a wishlist feature. Let's add Interlaken:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "add Interlaken to a wishlist"}'
```

```
Interlaken has been added to your wishlist! 🎉 You're going to love the adventure and stunning scenery there. Let me know if you need help with accommodations or activities in the area!
```

And we can retrieve it later:

```bash
curl -X POST http://localhost:8080/api/chat \
  -H "Content-Type: application/json" \
  -d '{"message": "retrieve my wishlist"}'
```

```
Here's your current wishlist:

- **Interlaken (Bernese Oberland)** 🏞️

If you'd like to add more destinations, hotels, or activities, just let me know! 😊✨
```

The LLM decides when to call tools, what parameters to pass, and how to present the results — you just define the tools and let the framework handle the rest.

## Conclusions

AI-powered assistants are a natural fit for travel apps. Users can ask questions in plain language — "find me a cozy ski town" or "add that to my wishlist" — and the system understands intent, not just keywords.

In this demo, Micronaut gives us a lightweight, fast-starting framework that's ideal for cloud deployments. LangChain4j handles the AI orchestration — chat memory, tool calling, and the RAG pipeline — with clean, declarative abstractions. And Oracle AI Database stores our vectors right alongside application data, so we get powerful similarity search without the overhead of a separate vector database.

Together, these technologies make building intelligent applications surprisingly straightforward. You can learn more at [micronaut.io](https://micronaut.io) and [graalvm.org](https://graalvm.org).
