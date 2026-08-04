# Swiss Travel Advisor: Oracle Chat Memory Follow-up

## Goal

Extend the demo with persistent, per-conversation chat memory in Oracle AI Database.
This turns the current sequence of independent requests into a natural multi-turn
travel conversation and completes the story already told by the application:

- Relational rows store destinations, hotels, activities, and wishlist items.
- Vector columns support semantic travel search.
- Spatial columns support location-aware search.
- Chat-memory JSON stores previous user and assistant messages.

The concise demo message is:

> Oracle AI Database stores the assistant's domain data, vectors, spatial data,
> wishlist, and conversational memory.

Relevant upstream changes:

- [LangChain4j PR #5351: Add Oracle Database for Chat Memory](https://github.com/langchain4j/langchain4j/pull/5351)
- [Micronaut LangChain4j PR #343: Add auto-configuration for Oracle Chat Memory](https://github.com/micronaut-projects/micronaut-langchain4j/pull/343)
- [Micronaut LangChain4j chat-memory documentation](https://micronaut-projects.github.io/micronaut-langchain4j/latest/guide/#chatMemory)

## Why This Is Valuable

The existing demo requests contain all important information explicitly:

```text
recommend best ski resorts
add Interlaken to my wishlist
retrieve my wishlist
```

Persistent chat memory enables more natural follow-ups:

```text
Recommend three peaceful mountain destinations without a car.
Which of those is car-free?
What was the second one?
Add it to my wishlist.
```

This clearly demonstrates four different responsibilities:

- **Vector search** finds destinations that match the meaning of a request.
- **Chat memory** resolves conversational references such as "those," "the
  second one," and "it."
- **Tools** perform searches and actions selected by the model.
- **Wishlist persistence** stores durable application state.

Chat memory must not become the source of truth for the wishlist. It is recent
conversational context; the wishlist remains authoritative business data.

## Upstream Behavior to Explain

`OracleChatMemoryStore` stores all messages for one memory ID as a JSON array in
one database row. Its default schema names are:

- Table: `CHAT_MEMORY`
- ID column: `MEMORY_ID`
- JSON content column: `CONTENT`

The table must already exist. The store reads the JSON array, replaces it when
messages change, and deletes the row when a memory is cleared.

Micronaut LangChain4j PR #343 adds configuration that creates an
`OracleChatMemoryStore` from an existing Micronaut `DataSource`. The segment
below `oracle` maps to a datasource name, so `default` uses the default
datasource and other named datasources can have separate stores.

## Release and Version Check

- [ ] Before implementation, verify that a Micronaut LangChain4j release from
  the `2.1.x` branch is available and managed by the Micronaut platform version
  used in this project.
- [ ] Verify that the managed LangChain4j version contains
  `dev.langchain4j.store.memory.chat.oracle.OracleChatMemoryStore`.
- [ ] Do not force mismatched Micronaut LangChain4j and LangChain4j versions
  without testing their BOM compatibility.

As of 2026-07-16, PR #343 is merged into the `2.1.x` branch, while the latest
published Micronaut LangChain4j GitHub release is `2.0.1`. Until a compatible
release is published, use a snapshot only for development or present the work as
an upcoming feature.

## Implementation Checklist

### 1. Dependency

- [ ] Add `micronaut-langchain4j-store-oracle` once a compatible version is
  available. This module is also the home of the Oracle embedding-store
  integration, so no separate chat-memory artifact is required.

```xml
<dependency>
    <groupId>io.micronaut.langchain4j</groupId>
    <artifactId>micronaut-langchain4j-store-oracle</artifactId>
    <scope>compile</scope>
</dependency>
```

### 2. Flyway Migration

- [ ] Add a new migration rather than editing `V1__create_schema.sql`.
- [ ] Create the chat-memory table before enabling the store.
- [ ] Confirm that the chosen JSON constraint works on the Oracle Database
  editions used by both the local container and the hosted demo.

Suggested migration:

```sql
CREATE TABLE chat_memory (
    memory_id VARCHAR2(255) PRIMARY KEY,
    content   CLOB CHECK (content IS JSON)
);
```

Possible follow-up fields for production-oriented discussion:

```sql
created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL,
updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP NOT NULL
```

The upstream store does not manage these timestamp fields, so adding them would
require suitable database defaults/triggers or a custom store.

### 3. Configuration

- [ ] Disable the default in-memory store so there is one unambiguous
  `ChatMemoryStore` implementation.
- [ ] Enable the Oracle store and configure its schema names.
- [ ] Set a bounded message window to control prompt size and token use.

Suggested `application.properties` entries:

```properties
langchain4j.chat-memory-store.inmemory.enabled=false
langchain4j.chat-memory-store.oracle.default.enabled=true
langchain4j.chat-memory-store.oracle.default.table-name=CHAT_MEMORY
langchain4j.chat-memory-store.oracle.default.memory-id-column-name=MEMORY_ID
langchain4j.chat-memory-store.oracle.default.content-column-name=CONTENT
langchain4j.chat-memory-store.message-window.max-messages=20
```

### 4. AI Service

- [ ] Add an explicit `@MemoryId` parameter to `SwissTravelAssistant.chat`.
- [ ] Keep `@UserMessage` explicit for readability.
- [ ] Verify that the generated AI Service uses the configured Oracle-backed
  message-window builder.

Proposed interface shape:

```java
import dev.langchain4j.service.MemoryId;
import dev.langchain4j.service.UserMessage;

@AiService(tools = TravelTools.class)
public interface SwissTravelAssistant {

    @SystemMessage("""
            ...
            """)
    String chat(
            @MemoryId String conversationId,
            @UserMessage String message);
}
```

Important: without `@MemoryId`, LangChain4j uses `"default"` as the memory ID.
Using that in a multi-user demo could mix unrelated users' conversations.

### 5. HTTP API Contract

- [ ] Change `ChatRequest` to accept both `conversationId` and `message`.
- [ ] Generate a UUID when the client does not supply a conversation ID.
- [ ] Return the conversation ID with the answer so a client can continue the
  same conversation.
- [ ] Return JSON rather than plain text after adding response metadata.
- [ ] Add the conversation ID to the GET endpoint or remove/deprecate that
  convenience endpoint to avoid accidental use of shared memory.
- [ ] Validate that the message is not blank and place reasonable limits on
  the ID and message lengths.

Suggested API records:

```java
@Serdeable
public record ChatRequest(String conversationId, String message) {}

@Serdeable
public record ChatResponse(String conversationId, String answer) {}
```

Suggested behavior:

```text
POST /api/chat with no conversationId
  -> generate UUID
  -> call assistant.chat(UUID, message)
  -> return { conversationId, answer }

POST /api/chat with conversationId
  -> call assistant.chat(conversationId, message)
  -> return the same conversationId with the answer
```

### 6. Concurrency

The current controller puts all requests behind one global `chatLock`. That is
safe for a small stage demo but serializes unrelated users.

- [ ] Test whether the selected LangChain4j/Micronaut versions safely serialize
  updates for the same memory ID.
- [ ] Replace the global lock with per-conversation serialization if concurrent
  requests for different conversations should proceed independently.
- [ ] Do not simply remove locking without testing. The Oracle store performs a
  read/modify/write of the complete JSON array; concurrent requests using the
  same memory ID could overwrite one another.
- [ ] Document that a production service may need optimistic locking,
  application-level serialization, or a custom append-oriented schema.

### 7. Conversation Lifecycle

- [ ] Add an endpoint or service operation to delete a conversation using
  `ChatMemoryStore.deleteMessages(memoryId)`.
- [ ] Decide whether a client may choose arbitrary memory IDs or should receive
  server-generated opaque IDs only.
- [ ] Define retention and cleanup for abandoned conversations.
- [ ] Avoid logging full chat content or memory IDs at inappropriate log levels.
- [ ] Treat stored prompts as potentially sensitive user data and cover access,
  deletion, and retention in any production notes.

### 8. Wishlist Scope

The current `wishlist_items` table has no user or conversation column, so the
wishlist is global even after chat memory becomes isolated.

- [ ] Decide whether that is acceptable for the single-user demo.
- [ ] If claiming multi-user support, add a user/session owner column and scope
  every wishlist query and mutation by that owner.
- [ ] Do not automatically equate a conversation ID with a durable user ID
  without explaining the lifecycle difference.
- [ ] Reconsider the system instruction that proactively adds an item whenever
  a user merely "expresses interest"; explicit confirmation is easier to
  understand and safer in a multi-user demo.

## Tests

- [ ] First request without an ID returns a newly generated conversation ID.
- [ ] A follow-up with the returned ID can refer to earlier recommendations.
- [ ] Two different memory IDs do not see each other's messages.
- [ ] Restart the application and confirm that the same ID restores context.
- [ ] A bounded message window evicts old messages as expected.
- [ ] Deleting a conversation removes its `CHAT_MEMORY` row.
- [ ] Blank messages and invalid/oversized IDs are rejected.
- [ ] Simultaneous requests for different IDs can run independently.
- [ ] Simultaneous requests for the same ID do not lose messages.
- [ ] Build and run the Native Image executable with Oracle-backed memory.
- [ ] Verify that existing vector search, spatial search, tools, and wishlist
  behavior still work.

Useful database inspection during testing:

```sql
SELECT memory_id, content
FROM chat_memory;
```

## Recommended Live Demo

Use a fixed ID on stage so it is easy to reuse after restarting the process:

```bash
http POST :8080/api/chat \
  conversationId=travel-demo-1 \
  message="Recommend three peaceful mountain destinations without a car"
```

Follow up without repeating the original constraints:

```bash
http POST :8080/api/chat \
  conversationId=travel-demo-1 \
  message="Which one is best for a weekend?"
```

Stop and restart the Native Image executable while leaving Oracle Database
running, then ask:

```bash
http POST :8080/api/chat \
  conversationId=travel-demo-1 \
  message="Add your second recommendation to my wishlist"
```

Finally:

```bash
http POST :8080/api/chat \
  conversationId=travel-demo-1 \
  message="What is on my wishlist, and why did you recommend it?"
```

Expected story:

1. Vector and spatial search find relevant destinations.
2. Chat memory preserves the recommendations and their order.
3. Restarting the native executable does not lose the conversation.
4. The model resolves "your second recommendation."
5. LangChain4j invokes the wishlist tool.
6. The wishlist remains durable application state rather than model memory.

For a reliable stage demo, pre-run the exact prompts, retain the same database,
record the conversation ID, and keep a fallback recording or captured output.

## Architecture Diagram Update

- [ ] Add `CHAT_MEMORY` to the Oracle AI Database area.
- [ ] Label the database roles separately: relational data, vectors, spatial
  data, and conversation JSON.
- [ ] Show `conversationId` flowing from the client through `/api/chat` to the
  AI Service and `ChatMemoryStore`.
- [ ] Show that retrieved messages are included in the model request.
- [ ] Keep the wishlist/tool path separate from chat memory to reinforce the
  difference between conversational and business state.

## Suggested Blog Update

Replace or expand the existing short **Chat Memory** section with:

> Chat memory makes follow-up questions possible. After recommending several
> destinations, the assistant can understand requests such as "Which one is
> car-free?" or "Add the second one to my wishlist."
>
> By default, chat messages can be held in application memory. For this demo,
> we persist them in Oracle AI Database using LangChain4j's
> `OracleChatMemoryStore`. Each conversation is associated with a unique memory
> ID, and its messages are stored as JSON. Micronaut configures the store from
> the application properties and datasource.
>
> This separates two kinds of memory: conversational memory gives the model
> recent context, while the wishlist represents durable application data.
> Because conversational memory resides in the database, it survives restarts
> and can be shared by multiple application instances.

Suggested transition into the Native Image section:

> Persisting conversation state outside the process also complements GraalVM
> Native Image. The native executable can start quickly, stop, or be replaced,
> while the user's conversation remains available in Oracle AI Database.

Suggested conclusion addition:

> Oracle AI Database now serves several roles in one application: relational
> storage for travel data and wishlists, vector search for semantic discovery,
> spatial search for nearby recommendations, and JSON-backed chat memory for
> natural multi-turn conversations.

## Editorial Notes

- [ ] Use **LangChain4j** consistently; avoid `Langchain4j`.
- [ ] Be precise that the model itself remains stateless. The application loads
  stored messages and sends the relevant history with each request.
- [ ] Do not describe chat memory as long-term user knowledge or as a substitute
  for domain persistence.
- [ ] Call out that message-window limits control how much history is sent to
  the model, even though a store is persistent.
- [ ] Avoid claiming multi-instance safety until concurrent updates for one
  memory ID have been tested.
- [ ] Re-run and update binary size, startup time, and memory measurements after
  adding the store dependency and migration.
- [ ] When discussing GraalVM Native Image, use "JVM application," "JVM
  library," "application," or "native executable" rather than "Java."

## Optional Follow-ups

- [ ] Add a small web client that stores `conversationId` in local storage.
- [ ] Show a "New conversation" action that discards the current ID.
- [ ] Add an explicit "Delete conversation" action.
- [ ] Add observability around memory loads, updates, message counts, latency,
  and token usage without exposing message content.
- [ ] Compare short-term message-window memory with a future summarized-memory
  strategy for long conversations.
- [ ] Consider associating conversations with authenticated users if the demo
  later adds security.

---

# Phoenix LLM Tracing: Next Steps

Last updated: 2026-07-30

The application-side tracing integration and `compose.yaml` are implemented.
Complete these steps locally to exercise and visually verify the integration
with Podman.

## Immediate Next Actions

- [ ] Import the `Oracle Secure Web Gateway` corporate CA into the Podman
  machine and update its trust store.
- [ ] Run `podman-compose up -d phoenix` and verify
  <http://localhost:6006>.
- [ ] Replace the stale Oracle Autonomous Database connection URL that returns
  `ORA-12514`.
- [ ] Start the application with `MICRONAUT_ENVIRONMENTS=phoenix`, send one
  chat request, and inspect the resulting trace.

## Implemented

- [x] Export HTTP and LangChain4j chat-model spans over OTLP.
- [x] Add OpenInference attributes for Phoenix LLM rendering.
- [x] Add dedicated spans for all `TravelTools` methods.
- [x] Add automatic JDBC query spans.
- [x] Make prompt and response capture configurable.
- [x] Pin the Phoenix container image.
- [x] Run `./mvnw clean test`.
- [x] Build the 136 MB native executable with the tracing additions.
- [x] Start the `podman-machine-default` Podman machine.

## Start Phoenix

- [x] Ensure the Podman machine is running:

  ```bash
  podman machine start
  ```

- [ ] Start Phoenix:

  ```bash
  podman-compose up -d phoenix
  ```

  Current blocker: the Podman VM does not trust the corporate
  `Oracle Secure Web Gateway` issuer used for Docker Hub traffic.

  1. Obtain the approved **CA certificate**, in PEM format, from the Oracle IT
     security portal or support team. Ask for the root or intermediate CA that
     signs certificates issued by `Oracle Secure Web Gateway`; do not save a
     certificate copied from an arbitrary website. A search of this Mac's
     System and system-root keychains did not find a certificate with that
     name.

     If IT supplies a DER-encoded `.cer` file, convert it to PEM:

     ```bash
     openssl x509 \
       -inform DER \
       -in ~/Downloads/oracle-secure-web-gateway-ca.cer \
       -out /tmp/oracle-secure-web-gateway-ca.pem
     ```

     If IT supplies a PEM file, copy it outside this repository:

     ```bash
     cp ~/Downloads/oracle-secure-web-gateway-ca.pem \
       /tmp/oracle-secure-web-gateway-ca.pem
     ```

  2. Inspect the certificate before trusting it:

     ```bash
     openssl x509 \
       -in /tmp/oracle-secure-web-gateway-ca.pem \
       -noout -subject -issuer -dates -fingerprint -sha256

     openssl x509 \
       -in /tmp/oracle-secure-web-gateway-ca.pem \
       -noout -text | grep -A 1 "Basic Constraints"
     ```

     Confirm the subject, validity dates, and SHA-256 fingerprint with Oracle
     IT. `Basic Constraints` must identify it as a CA (`CA:TRUE`). Stop if the
     fingerprint does not match the value supplied by IT.

  3. Copy the verified public certificate into the running Podman machine:

     ```bash
     podman machine cp \
       /tmp/oracle-secure-web-gateway-ca.pem \
       podman-machine-default:/tmp/oracle-secure-web-gateway-ca.pem
     ```

  4. Install it into the Fedora CoreOS trust-anchor directory and rebuild the
     VM trust store:

     ```bash
     podman machine ssh podman-machine-default \
       sudo install -m 0644 \
       /tmp/oracle-secure-web-gateway-ca.pem \
       /etc/pki/ca-trust/source/anchors/oracle-secure-web-gateway-ca.pem

     podman machine ssh podman-machine-default sudo update-ca-trust
     ```

  5. Confirm that the CA is visible inside the VM:

     ```bash
     podman machine ssh podman-machine-default \
       trust list --filter=ca-anchors
     ```

     Search the output for the certificate subject verified in step 2.

  6. Test certificate validation by pulling the pinned image, then start
     Phoenix:

     ```bash
     podman pull arizephoenix/phoenix:version-17.5.0
     podman-compose up -d phoenix
     podman-compose logs phoenix
     ```

     Do not work around certificate errors with `--tls-verify=false`. The
     installed CA survives Podman machine restarts, but it must be installed
     again if `podman machine rm` or `podman machine reset` recreates the VM.

  7. Optional cleanup after the pull succeeds:

     ```bash
     rm /tmp/oracle-secure-web-gateway-ca.pem
     podman machine ssh podman-machine-default \
       rm /tmp/oracle-secure-web-gateway-ca.pem
     ```

     This removes only the temporary copies; the trusted copy remains under
     `/etc/pki/ca-trust/source/anchors`.

- [ ] Confirm that the container is healthy enough to serve the UI:

  ```bash
  podman ps
  podman-compose logs phoenix
  ```

- [ ] Open <http://localhost:6006> and confirm that the Phoenix UI loads.

## Generate and Inspect a Trace

- [ ] Refresh the Oracle database connection variables. They are present, but
  the configured ADB service currently returns `ORA-12514` because it is no
  longer registered with the listener.

- [ ] Start the application with the opt-in tracing environment:

  ```bash
  MICRONAUT_ENVIRONMENTS=phoenix ./mvnw mn:run
  ```

- [ ] Send a request that should exercise tool calling:

  ```bash
  curl -X POST http://localhost:8080/api/chat \
    -H "Content-Type: application/json" \
    -d '{"message":"find quiet lakeside hotels near Lucerne under 250 CHF"}'
  ```

- [ ] In Phoenix, open the `swiss-travel-advisor` project and verify:

  - The HTTP request and `chat gpt-5.4-mini` spans share one trace.
  - Multiple model calls appear when the assistant selects and consumes a tool.
  - A `tool searchNearbyHotels` span contains the embedding and JDBC work.
  - The LLM spans show model name, duration, status, and token usage.
  - Input and output show messages, tool requests, and tool results.

- [ ] Exercise an error case, such as temporarily using an invalid API key, and
  confirm that the LLM span is marked as an error. Restore the valid key
  immediately afterward.

## Privacy and Configuration Checks

- [ ] Confirm that prompt and response content is appropriate to store locally.
  The `phoenix` environment enables content capture for debugging.

- [ ] Verify metadata-only tracing by restarting the application with:

  ```bash
  AI_TRACING_INCLUDE_CONTENT=false \
    MICRONAUT_ENVIRONMENTS=phoenix ./mvnw mn:run
  ```

- [ ] If traces should go to a shared collector, set
  `OTEL_EXPORTER_OTLP_ENDPOINT` and `OTEL_EXPORTER_OTLP_PROTOCOL` instead of
  using the local endpoint.

## Finish the Verification

- [x] Run the build check:

  ```bash
  ./mvnw test
  ```

- [ ] Stop Phoenix when finished:

  ```bash
  podman-compose stop phoenix
  ```

  Trace data remains in the named `phoenix-data` volume. Do not use
  `podman-compose down -v` unless deleting that trace history is intentional.

- [x] Pin `arizephoenix/phoenix` rather than relying on the floating `latest`
  tag. Update the pinned version deliberately after testing new releases.

- [ ] Add embedding-model spans if startup backfill and per-tool embedding calls
  should also appear as first-class AI operations in Phoenix.

- [ ] Revisit the separate `native-report` Maven profile. Its existing
  `--emit=build-report` and `ReportDynamicAccess` flags are not supported by the
  installed GraalVM CE 25.1.3; the normal native executable build succeeds.
