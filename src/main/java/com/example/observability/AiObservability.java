package com.example.observability;

import io.micronaut.context.annotation.Value;
import io.micronaut.serde.ObjectMapper;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import io.opentelemetry.context.Scope;
import jakarta.inject.Singleton;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/** Creates the OpenInference span hierarchy used by the conference demo. */
@Singleton
public final class AiObservability {
    private static final String JSON_MIME_TYPE = "application/json";
    private static final String TEXT_MIME_TYPE = "text/plain";

    private final Tracer tracer;
    private final ObjectMapper objectMapper;
    private final PhoenixAnnotationPublisher annotationPublisher;
    private final boolean enabled;
    private final boolean includeContent;
    private final String embeddingModelName;
    private final ThreadLocal<TurnState> turnState = new ThreadLocal<>();

    public AiObservability(
            OpenTelemetry openTelemetry,
            ObjectMapper objectMapper,
            PhoenixAnnotationPublisher annotationPublisher,
            @Value("${app.ai.tracing.enabled:false}") boolean enabled,
            @Value("${app.ai.tracing.include-content:false}") boolean includeContent,
            @Value("${langchain4j.open-ai.embedding-model.model-name:text-embedding-3-small}") String embeddingModelName) {
        this.tracer = openTelemetry.getTracer("com.example.swiss-travel-advisor.ai");
        this.objectMapper = objectMapper;
        this.annotationPublisher = annotationPublisher;
        this.enabled = enabled;
        this.includeContent = includeContent;
        this.embeddingModelName = embeddingModelName;
    }

    public AgentTurn traceAgentTurn(String conversationId, String input, Supplier<String> operation) {
        if (!enabled) {
            return new AgentTurn(operation.get(), null);
        }

        Span span = tracer.spanBuilder("Swiss Travel Advisor")
                .setNoParent()
                .setSpanKind(SpanKind.INTERNAL)
                .startSpan();
        TurnState state = new TurnState(conversationId, input);
        turnState.set(state);
        span.setAttribute("openinference.span.kind", "AGENT");
        span.setAttribute("gen_ai.operation.name", "invoke_agent");
        span.setAttribute("session.id", conversationId);
        setContent(span, "input", input, TEXT_MIME_TYPE);

        try (Scope ignored = span.makeCurrent()) {
            String output = operation.get();
            setContent(span, "output", output, TEXT_MIME_TYPE);
            List<PhoenixAnnotationPublisher.Evaluation> evaluations = evaluate(state, output);
            for (PhoenixAnnotationPublisher.Evaluation evaluation : evaluations) {
                span.setAttribute("evaluation." + evaluation.name() + ".score", evaluation.score());
                span.setAttribute("evaluation." + evaluation.name() + ".label", evaluation.label());
            }
            span.setStatus(StatusCode.OK);
            annotationPublisher.publish(span.getSpanContext().getSpanId(), evaluations);
            return new AgentTurn(output, span.getSpanContext().getSpanId());
        } catch (RuntimeException error) {
            recordError(span, error);
            throw error;
        } finally {
            turnState.remove();
            span.end();
        }
    }

    public String traceTool(String name, Map<String, ?> parameters, Supplier<String> operation) {
        TurnState state = turnState.get();
        if (state != null) {
            state.tools.add(name);
        }
        if (!enabled) {
            return operation.get();
        }

        return trace("tool " + name, "TOOL", span -> {
            span.setAttribute("gen_ai.operation.name", "execute_tool");
            span.setAttribute("tool.name", name);
            setJsonContent(span, "input", parameters);
            if (includeContent) {
                setIfPresent(span, "tool.parameters", toJson(parameters));
            }
        }, operation, TEXT_MIME_TYPE);
    }

    public float[] traceEmbedding(String input, Supplier<float[]> operation) {
        if (!enabled) {
            return operation.get();
        }

        Span span = tracer.spanBuilder("embed query")
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        span.setAttribute("openinference.span.kind", "EMBEDDING");
        applySession(span);
        span.setAttribute("gen_ai.operation.name", "embeddings");
        span.setAttribute("embedding.model_name", embeddingModelName);
        setContent(span, "input", input, TEXT_MIME_TYPE);
        try (Scope ignored = span.makeCurrent()) {
            float[] result = operation.get();
            span.setAttribute("embedding.vector.dimension", result.length);
            span.setStatus(StatusCode.OK);
            return result;
        } catch (RuntimeException error) {
            recordError(span, error);
            throw error;
        } finally {
            span.end();
        }
    }

    public <T> List<T> traceRetriever(
            String name,
            Map<String, ?> attributes,
            Supplier<List<T>> operation) {
        return traceRetriever(name, attributes, operation, null);
    }

    public <T> List<T> traceRetriever(
            String name,
            Map<String, ?> attributes,
            Supplier<List<T>> operation,
            Function<T, RetrievalDocument> documentMapper) {
        if (!enabled) {
            return operation.get();
        }

        Span span = tracer.spanBuilder(name)
                .setSpanKind(SpanKind.CLIENT)
                .startSpan();
        span.setAttribute("openinference.span.kind", "RETRIEVER");
        applySession(span);
        span.setAttribute("db.system", "oracle");
        span.setAttribute("retrieval.operation", "vector_search");
        attributes.forEach((key, value) -> setIfPresent(span, "retrieval." + key, value));
        Object query = attributes.get("query");
        if (query != null) {
            setContent(span, "input", query.toString(), TEXT_MIME_TYPE);
        }
        try (Scope ignored = span.makeCurrent()) {
            List<T> results = operation.get();
            // OpenInference reserves retrieval.documents for an array of
            // document objects. A nested `.count` attribute makes Phoenix
            // deserialize that field as an object and breaks its retriever UI.
            span.setAttribute("retrieval.result_count", results.size());
            if (documentMapper != null) {
                setRetrievalDocuments(span, results, documentMapper);
            }
            span.setStatus(StatusCode.OK);
            return results;
        } catch (RuntimeException error) {
            recordError(span, error);
            throw error;
        } finally {
            span.end();
        }
    }

    private String trace(
            String spanName,
            String openInferenceKind,
            java.util.function.Consumer<Span> configure,
            Supplier<String> operation,
            String outputMimeType) {
        Span span = tracer.spanBuilder(spanName).setSpanKind(SpanKind.INTERNAL).startSpan();
        span.setAttribute("openinference.span.kind", openInferenceKind);
        applySession(span);
        configure.accept(span);
        try (Scope ignored = span.makeCurrent()) {
            String output = operation.get();
            setContent(span, "output", output, outputMimeType);
            span.setStatus(StatusCode.OK);
            return output;
        } catch (RuntimeException error) {
            recordError(span, error);
            throw error;
        } finally {
            span.end();
        }
    }

    private List<PhoenixAnnotationPublisher.Evaluation> evaluate(TurnState state, String output) {
        List<PhoenixAnnotationPublisher.Evaluation> evaluations = new ArrayList<>();
        boolean hasResponse = output != null && !output.isBlank();
        evaluations.add(evaluation("response_present", hasResponse,
                hasResponse ? "The assistant returned a response." : "The assistant returned no response."));

        String expectedTool = expectedSearchTool(state.input);
        if (expectedTool != null) {
            boolean correct = state.tools.contains(expectedTool);
            evaluations.add(evaluation("tool_selection", correct,
                    correct ? "The expected search tool was called: " + expectedTool
                            : "Expected " + expectedTool + " but observed " + state.tools + "."));
        }

        boolean wishlistMutation = state.tools.contains("addToWishlist");
        boolean explicitPermission = hasAny(state.input.toLowerCase(Locale.ROOT),
                "add ", "save ", "bookmark", "wishlist");
        boolean safe = !wishlistMutation || explicitPermission;
        evaluations.add(evaluation("wishlist_permission", safe,
                safe ? "No wishlist mutation occurred without explicit permission."
                        : "The wishlist was modified without an explicit user request."));
        return evaluations;
    }

    private PhoenixAnnotationPublisher.Evaluation evaluation(String name, boolean passed, String explanation) {
        return new PhoenixAnnotationPublisher.Evaluation(
                name, passed ? "pass" : "fail", passed ? 1.0 : 0.0, explanation);
    }

    private String expectedSearchTool(String input) {
        String normalized = input.toLowerCase(Locale.ROOT);
        boolean searchIntent = hasAny(normalized,
                "find", "search", "recommend", "suggest", "show", "looking for", "look for", "where can i");
        if (!searchIntent) {
            return null;
        }

        boolean nearby = hasAny(normalized, " near ", " around ", " within ", " in zermatt", " in interlaken",
                " in lucerne", " in lausanne", " in st. moritz", " in lugano", " in zurich");
        if (hasAny(normalized, "hotel", "accommodation", "stay")) {
            return nearby ? "searchNearbyHotels" : "searchHotels";
        }
        if (hasAny(normalized, "activit", "things to do", "experience")) {
            return nearby ? "searchNearbyActivities" : "searchActivities";
        }
        if (hasAny(normalized, "destination", "place to visit", "town", "village")) {
            return nearby ? "searchNearbyDestinations" : "searchDestinations";
        }
        return null;
    }

    private boolean hasAny(String value, String... candidates) {
        for (String candidate : candidates) {
            if (value.contains(candidate)) {
                return true;
            }
        }
        return false;
    }

    private void setJsonContent(Span span, String prefix, Object value) {
        if (includeContent) {
            setIfPresent(span, prefix + ".value", toJson(value));
            span.setAttribute(prefix + ".mime_type", JSON_MIME_TYPE);
        }
    }

    private void setContent(Span span, String prefix, String value, String mimeType) {
        if (includeContent && value != null) {
            span.setAttribute(prefix + ".value", value);
            span.setAttribute(prefix + ".mime_type", mimeType);
        }
    }

    private <T> void setRetrievalDocuments(
            Span span,
            List<T> results,
            Function<T, RetrievalDocument> documentMapper) {
        for (int index = 0; index < results.size(); index++) {
            RetrievalDocument document = documentMapper.apply(results.get(index));
            if (document == null) {
                continue;
            }
            String prefix = "retrieval.documents." + index + ".document.";
            setIfPresent(span, prefix + "id", document.id());
            setIfPresent(span, prefix + "score", document.score());
            if (includeContent) {
                setIfPresent(span, prefix + "content", document.content());
                Map<String, Object> metadata = new LinkedHashMap<>();
                metadata.put("rank", index + 1);
                if (document.metadata() != null) {
                    document.metadata().forEach(metadata::put);
                }
                setIfPresent(span, prefix + "metadata", toJson(metadata));
            }
        }
    }

    private String toJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (IOException error) {
            return String.valueOf(value);
        }
    }

    private void setIfPresent(Span span, String name, Object value) {
        if (value instanceof Integer integer) {
            span.setAttribute(name, integer.longValue());
        } else if (value instanceof Long longValue) {
            span.setAttribute(name, longValue);
        } else if (value instanceof Number number) {
            span.setAttribute(name, number.doubleValue());
        } else if (value != null && !value.toString().isBlank()) {
            span.setAttribute(name, value.toString());
        }
    }

    private void recordError(Span span, RuntimeException error) {
        span.recordException(error);
        span.setStatus(StatusCode.ERROR, error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage());
        span.setAttribute("error.type", error.getClass().getName());
    }

    public String currentSessionId() {
        TurnState state = turnState.get();
        return state == null ? null : state.sessionId;
    }

    public record AgentTurn(String output, String spanId) {
    }

    public record RetrievalDocument(String id, String content, Double score, Map<String, ?> metadata) {
    }

    private void applySession(Span span) {
        String sessionId = currentSessionId();
        if (sessionId != null) {
            span.setAttribute("session.id", sessionId);
        }
    }

    private static final class TurnState {
        private final String sessionId;
        private final String input;
        private final Set<String> tools = new LinkedHashSet<>();

        private TurnState(String sessionId, String input) {
            this.sessionId = sessionId;
            this.input = input;
        }
    }
}
