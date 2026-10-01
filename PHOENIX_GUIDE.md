# Phoenix Guide for the Swiss Travel Advisor Demo

Phoenix shows how one user request moves through the model, application tools,
embedding model, and Oracle Database.

## The trace to show

Use a search request such as:

> Find a relaxing spa hotel near Lucerne under CHF 300.

In **Traces**, select the **Swiss Travel Advisor** trace and expand
**`tool searchNearbyHotels`**:

```text
AGENT: Swiss Travel Advisor
├── LLM: model chooses a tool
├── TOOL: searchNearbyHotels
│   ├── EMBEDDING: embed query
│   └── RETRIEVER: Oracle hotel vector + spatial search
└── LLM: model writes the final answer
```

The important stage action is:

1. Open the hotel-search `AGENT` trace.
2. Expand **`tool searchNearbyHotels`**.
3. Select **Oracle hotel vector + spatial search**.
4. Show its input, filters, result count, and ranked documents.

To list every retrieval directly, open **Spans** and filter:

```text
span_kind == 'RETRIEVER'
```

## Why only the preference is embedded

The language model maps the user sentence to typed tool arguments:

```text
User sentence
    ↓ language model
searchNearbyHotels(
    query = "relaxing spa hotel",
    nearDestinationName = "Lucerne",
    radiusKm = 15,
    maxPrice = 300
)
    ↓ Java
Oracle vector + spatial + price search
```

Each part is handled by the mechanism best suited to it:

| Tool argument | Meaning | How it is applied |
| --- | --- | --- |
| `query = "relaxing spa hotel"` | Soft preference | Converted to an embedding for semantic similarity |
| `nearDestinationName = "Lucerne"` | Location | Resolved to stored coordinates |
| `radiusKm = 15` | Distance limit | Applied as an exact spatial constraint |
| `maxPrice = 300` | Budget | Applied as an exact numeric constraint |

Phoenix shows `relaxing spa hotel` as the `EMBEDDING` input because that is the
text converted into a 1,536-dimensional vector. It does not display all 1,536
numbers. The vector dimension is available in the span attributes.

Embedding price or distance would make those constraints approximate. Keeping
them as structured values lets Oracle enforce them exactly while ranking the
remaining hotels by semantic similarity.

## What the retriever proves

The `RETRIEVER` span represents the actual Oracle search, not merely the
model's intention to search. It records:

- The semantic query
- Entity type and result count
- Location anchor and radius
- Maximum price when supplied
- Ranked document IDs and content
- Similarity score, rank, and raw cosine distance

Interpret the scores as:

```text
similarity score = 1 - cosine distance
```

- Higher similarity is better.
- Lower cosine distance is better.
- Rank `1` is the first result returned after all constraints are applied.

## Why a wishlist trace has no retriever

Each user message creates a separate trace. A request such as:

> Save the first hotel to my wishlist.

normally contains:

```text
AGENT → LLM → TOOL addToWishlist → LLM
```

It does not perform vector search, so it should not contain a `RETRIEVER` span.
The retrieval belongs to the earlier hotel-search trace in the same session.
Use the **Sessions** tab to move between those conversation turns.

## Span kinds used in the demo

| Kind | Meaning |
| --- | --- |
| `AGENT` | One complete user turn, including input, output, and evaluations |
| `LLM` | One chat-model call |
| `TOOL` | An application operation selected by the model |
| `EMBEDDING` | Conversion of preference text into a vector |
| `RETRIEVER` | Oracle vector or vector-plus-spatial search |

Automatic HTTP and JDBC wrapper spans are omitted so these AI spans remain the
focus of the demo.

## Evaluations

The `AGENT` span can contain these annotations:

| Annotation | Question answered |
| --- | --- |
| `response_present` | Did the assistant return a response? |
| `tool_selection` | Did it call the expected search tool? |
| `wishlist_permission` | Did it avoid changing the wishlist without permission? |
| `user_feedback` | Did the user mark the response as helpful? |

Scores are pass rates, not model confidence. A value of `1.00` means every
applicable evaluation passed; `0.50` means half passed. An annotation is absent
when it does not apply, such as `tool_selection` on a greeting.

## Generate presentation traces

```bash
./scripts/generate-demo-traces.sh
```

For a denser metrics chart:

```bash
DEMO_ROUNDS=3 ./scripts/generate-demo-traces.sh
```

Wait a few seconds for batched export, then open <http://localhost:6006> and
select the `swiss-travel-advisor` project.
