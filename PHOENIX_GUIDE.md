# Understanding Phoenix in the Swiss Travel Advisor Demo

This guide explains what the Phoenix screens mean in everyday language. It is
intended for anyone presenting, testing, or debugging the demo. You do not need
to know OpenTelemetry or OpenInference to use it.

## The short version

Each message sent to the Swiss Travel Advisor creates one AI trace. The trace
starts with an `AGENT` span named **Swiss Travel Advisor** and contains the work
performed for that message:

```text
AGENT: Swiss Travel Advisor
├── LLM: chat with the model
│   └── HTTP POST: the network request sent to OpenAI
├── TOOL: search, wishlist, or another application action
│   ├── EMBEDDING: turn search text into a vector
│   └── RETRIEVER: search Oracle Database
└── LLM: produce the final answer after seeing the tool result
    └── HTTP POST: the second network request sent to OpenAI
```

Not every request contains every type of span. A simple greeting may only need
one LLM call. A search normally includes an embedding and a retriever. A tool
call often causes two LLM calls: one to choose the tool and another to write the
final response after the tool finishes.

## What the span kinds mean

| Kind | Plain-language meaning |
| --- | --- |
| `AGENT` | The complete handling of one user message. This is the most useful place to see the original question, final response, and evaluations. |
| `LLM` | One call to the chat model. It shows the messages sent to the model, its response, token usage, latency, and cost. |
| `TOOL` | An application action selected by the model, such as searching hotels or adding an item to the wishlist. |
| `EMBEDDING` | Conversion of search text into a numeric vector used for semantic search. |
| `RETRIEVER` | The Oracle vector or vector-plus-spatial database search. |
| `unknown` | A normal technical span that Phoenix cannot classify as an AI operation. In this demo it is usually an HTTP server or client request. It does not mean an unknown user or an error. |

## What the `unknown POST` rows are

An indented `unknown` span named only `POST` beneath an LLM span is the outgoing
HTTP request to OpenAI. Expand it and inspect fields such as `server.address` or
`url.full`; these should identify the model endpoint.

These rows are not additional users or extra model calls. They are the network
layer underneath the corresponding LLM call.

Standalone HTTP rows come from the browser talking to the application:

| HTTP span | What caused it |
| --- | --- |
| `POST /api/chat` | The user sent a chat message. |
| `POST /api/feedback` | The user clicked thumbs up or thumbs down. |
| `GET /api/wishlist` | The page refreshed the current wishlist. |
| `GET /api/conversations` | The page refreshed the conversation list. |

The HTTP spans are useful for diagnosing network failures and latency. The
`AGENT`, `LLM`, `TOOL`, and `RETRIEVER` spans are normally more useful when
investigating the quality of an AI response.

## Traces and sessions

A **trace** represents one user turn: one question and the work needed to answer
it. A **session** groups multiple traces from the same conversation.

For example, this conversation is one session containing three traces:

1. "Find a quiet hotel near Lucerne."
2. "Save the first one to my wishlist."
3. "What is currently on my wishlist?"

The application uses its conversation ID as Phoenix's `session.id`, which is
how Phoenix knows that the three turns belong together.

Phoenix gets the input and output shown in the Sessions view from the root span
of each trace. The `AGENT` span is deliberately created as that root and carries
the user's message in `input.value` and the assistant's answer in
`output.value`.

Older sessions may show `--` for input and output. Those traces were recorded
before the AGENT-root fix, when an HTTP span was the trace root. Existing stored
traces are not rewritten automatically; generate new messages to see the
correct session previews.

## Understanding the annotation score chart

The Metrics screen plots the average annotation score in each time bucket. The
demo's annotations use binary scores:

- `1.00` means every evaluated item passed.
- `0.50` means half passed.
- `0.67` means approximately two out of three passed.
- `0.00` means every evaluated item failed.

With only a few traces, one failure can move the average dramatically. For
example, one pass and one failure produce `0.50`. Treat early charts as examples
rather than statistically reliable trends.

### `response_present`

A score of `1` means the assistant returned a non-empty response. This is a
basic availability check. It does not prove that the response was correct,
helpful, or grounded in the search results.

### `tool_selection`

A score of `1` means the application observed the search tool expected by its
rule-based evaluator. A score of `0` means the expected tool was not observed.

Open the annotation on the affected AGENT span to see an explanation such as:

```text
Expected searchNearbyHotels but observed [searchHotels].
```

This evaluator is intentionally simple. A failure can indicate a genuine model
routing problem, but it can also expose a limitation in the evaluator's keyword
rules. Always inspect the question and actual tool call before drawing a
conclusion.

### `wishlist_permission`

A score of `1` means the wishlist was not modified without an explicit request.
Doing nothing also passes this safety check. It measures permission safety, not
whether a requested wishlist operation succeeded.

### `user_feedback`

The chat interface adds feedback to the AGENT span:

- Thumbs up produces `helpful` with score `1`.
- Thumbs down produces `not_helpful` with score `0`.

The average is therefore the positive-feedback rate for the selected time
bucket. For example, `0.75` means three quarters of submitted ratings were
positive.

Feedback buttons currently appear on newly generated responses. Historical
messages loaded from Oracle chat memory do not contain their original Phoenix
span IDs, so the application cannot safely attach feedback from those old
messages to a specific span.

### Why some lines seem to be missing

Several annotation series may have exactly the same value and overlap on the
chart. A blue line at `1.00`, for example, can hide another series that is also
at `1.00`. Hover over the point or hide series using the legend to inspect them
individually.

An annotation can also be absent from a time bucket because it was not
applicable. For example, `tool_selection` is only produced when the evaluator
can infer an expected search tool from the user's request.

## Span annotations versus session annotations

The demo publishes `response_present`, `tool_selection`,
`wishlist_permission`, and `user_feedback` as **span annotations** on each
AGENT turn. They appear in project metrics and on the individual AGENT span.

The Annotations column on the Sessions screen is for **session annotations**,
which evaluate an entire conversation. That column remains empty because the
demo does not currently publish session-level evaluations.

This separation is intentional:

- "Did this response use the right tool?" belongs to one AGENT span.
- "Was the whole trip-planning conversation coherent?" belongs to the session.

To find the current annotations, open a session, select a trace, select its
**Swiss Travel Advisor** AGENT span, and open the annotations or evaluations
section.

Useful future session annotations could include `conversation_quality`,
`constraints_satisfied`, or an overall end-of-conversation user rating.

## Understanding ranked retrieval documents

Open a `RETRIEVER` span to inspect the Oracle search results. Each result is
exported as a ranked OpenInference retrieval document with:

- A stable catalog ID such as `hotel:10`.
- Human-readable content containing the name and description.
- A relevance score based on cosine similarity. Higher is better.
- A one-based rank showing its position in the returned list.
- The raw cosine distance. Lower is better.
- Useful metadata such as entity type, destination ID, season, or price.

Cosine similarity and cosine distance express the same comparison in opposite
directions:

```text
similarity score = 1 - cosine distance
```

Therefore:

- A high score and low distance indicate a closer semantic match.
- A lower score and higher distance indicate a weaker match.
- Rank `1` is the first result Oracle returned after applying semantic,
  location, and price constraints.

For nearby searches, the retriever also records the location anchor and search
radius. The current document metadata contains semantic cosine distance, not
the exact physical distance in kilometres for each result.

## A practical debugging workflow

When an answer looks wrong, inspect the trace from the outside inward:

1. Open the `AGENT` span and confirm the user input and final output.
2. Read the AGENT annotations for tool-selection or permission failures.
3. Inspect the first `LLM` span to see what tool the model requested.
4. Inspect the `TOOL` span to verify its arguments and returned text.
5. For searches, inspect the `RETRIEVER` documents, ranks, scores, and raw
   distances.
6. Inspect the final `LLM` span to see how the model turned the tool result into
   the answer.
7. Use the nested HTTP `POST` spans only when investigating provider latency or
   network errors.

This sequence usually reveals whether a problem came from model routing, tool
arguments, database retrieval, or final response generation.

## Quick reference

| If you see... | It means... |
| --- | --- |
| Session input/output is `--` | Usually an old trace whose AGENT span was not the root, or content capture was disabled. |
| `unknown POST` under an LLM | The network request to OpenAI. |
| `POST /api/chat` | The browser submitted a user message. |
| `POST /api/feedback` | The user submitted a thumb rating. |
| Empty Sessions annotation column | The demo has turn-level span annotations, not session-level annotations. |
| Annotation score `0.50` | Half of the applicable annotations in that bucket scored `1`. |
| Missing annotation line | It overlaps another line or no annotation of that type exists in the bucket. |
| Two LLM spans in one trace | The model selected a tool, then generated a final answer after the tool returned. |
| High retrieval score | Stronger semantic match. |
| Low cosine distance | Stronger semantic match. |

## Generating a useful sample

Run the trace generator and then wait a few seconds for batched OTLP export and
annotation publication:

```bash
./scripts/generate-demo-traces.sh
```

For a denser metrics chart:

```bash
DEMO_ROUNDS=3 ./scripts/generate-demo-traces.sh
```

Open Phoenix at <http://localhost:6006>, choose the
`swiss-travel-advisor` project, and start with the Sessions or Traces tab.
