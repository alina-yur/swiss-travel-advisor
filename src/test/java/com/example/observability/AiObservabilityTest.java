package com.example.observability;

import io.micronaut.serde.ObjectMapper;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import io.opentelemetry.context.Scope;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AiObservabilityTest {

    @Test
    void createsAgentToolEmbeddingAndRetrieverHierarchy() {
        CollectingSpanExporter exporter = new CollectingSpanExporter();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(provider)
                .build();
        PhoenixAnnotationPublisher publisher = new PhoenixAnnotationPublisher(
                ObjectMapper.getDefault(), false, "http://localhost:6006", "");
        AiObservability observability = new AiObservability(
                openTelemetry,
                ObjectMapper.getDefault(),
                publisher,
                true,
                true,
                "text-embedding-3-small");

        AiObservability.AgentTurn response = observability.traceAgentTurn(
                "conversation-7",
                "Find hotels near Lucerne",
                () -> observability.traceTool(
                        "searchNearbyHotels",
                        Map.of("query", "hotels", "location", "Lucerne"),
                        () -> {
                            observability.traceEmbedding("hotels", () -> new float[]{1.0f, 2.0f});
                            observability.traceRetriever(
                                    "Oracle hotel vector + spatial search",
                                    Map.of("entity.type", "hotel", "radius_km", 15.0),
                                    () -> List.of("Hotel A"),
                                    result -> new AiObservability.RetrievalDocument(
                                            "hotel:1",
                                            result,
                                            0.875,
                                            Map.of("cosine_distance", 0.125)));
                            return "Found Hotel A";
                        }));

        assertEquals("Found Hotel A", response.output());
        assertEquals(16, response.spanId().length());
        assertEquals(4, exporter.spans.size());

        SpanData agent = span(exporter.spans, "Swiss Travel Advisor");
        SpanData tool = span(exporter.spans, "tool searchNearbyHotels");
        SpanData embedding = span(exporter.spans, "embed query");
        SpanData retriever = span(exporter.spans, "Oracle hotel vector + spatial search");

        assertEquals("AGENT", attribute(agent, "openinference.span.kind"));
        assertEquals("TOOL", attribute(tool, "openinference.span.kind"));
        assertEquals("EMBEDDING", attribute(embedding, "openinference.span.kind"));
        assertEquals("RETRIEVER", attribute(retriever, "openinference.span.kind"));
        assertEquals("conversation-7", attribute(agent, "session.id"));
        assertEquals("conversation-7", attribute(tool, "session.id"));
        assertEquals(agent.getSpanId(), tool.getParentSpanId());
        assertEquals(tool.getSpanId(), embedding.getParentSpanId());
        assertEquals(tool.getSpanId(), retriever.getParentSpanId());
        assertEquals(1L, retriever.getAttributes().get(AttributeKey.longKey("retrieval.result_count")));
        assertEquals("hotel:1", attribute(retriever, "retrieval.documents.0.document.id"));
        assertEquals("Hotel A", attribute(retriever, "retrieval.documents.0.document.content"));
        assertEquals(0.875, retriever.getAttributes().get(
                AttributeKey.doubleKey("retrieval.documents.0.document.score")));
        assertTrue(attribute(retriever, "retrieval.documents.0.document.metadata")
                .contains("\"cosine_distance\":0.125"));
        assertTrue(tool.getEndEpochNanos() >= tool.getStartEpochNanos());
        provider.close();
    }

    @Test
    void createsAgentAsTraceRootWhenAnHttpSpanIsActive() {
        CollectingSpanExporter exporter = new CollectingSpanExporter();
        SdkTracerProvider provider = SdkTracerProvider.builder()
                .addSpanProcessor(SimpleSpanProcessor.create(exporter))
                .build();
        OpenTelemetrySdk openTelemetry = OpenTelemetrySdk.builder()
                .setTracerProvider(provider)
                .build();
        PhoenixAnnotationPublisher publisher = new PhoenixAnnotationPublisher(
                ObjectMapper.getDefault(), false, "http://localhost:6006", "");
        AiObservability observability = new AiObservability(
                openTelemetry,
                ObjectMapper.getDefault(),
                publisher,
                true,
                true,
                "text-embedding-3-small");

        Span httpSpan = openTelemetry.getTracer("test").spanBuilder("POST /api/chat").startSpan();
        try (Scope ignored = httpSpan.makeCurrent()) {
            observability.traceAgentTurn("conversation-8", "Hello", () -> "Grüezi");
        } finally {
            httpSpan.end();
        }

        SpanData agent = span(exporter.spans, "Swiss Travel Advisor");
        SpanData http = span(exporter.spans, "POST /api/chat");
        assertEquals("0000000000000000", agent.getParentSpanId());
        assertNotEquals(http.getTraceId(), agent.getTraceId());
        assertEquals("Hello", attribute(agent, "input.value"));
        assertEquals("Grüezi", attribute(agent, "output.value"));
        provider.close();
    }

    private static SpanData span(List<SpanData> spans, String name) {
        return spans.stream()
                .filter(span -> span.getName().equals(name))
                .findFirst()
                .orElseThrow();
    }

    private static String attribute(SpanData span, String name) {
        return span.getAttributes().get(AttributeKey.stringKey(name));
    }

    private static final class CollectingSpanExporter implements SpanExporter {
        private final List<SpanData> spans = new ArrayList<>();

        @Override
        public CompletableResultCode export(Collection<SpanData> spans) {
            this.spans.addAll(spans);
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode flush() {
            return CompletableResultCode.ofSuccess();
        }

        @Override
        public CompletableResultCode shutdown() {
            return CompletableResultCode.ofSuccess();
        }
    }
}
