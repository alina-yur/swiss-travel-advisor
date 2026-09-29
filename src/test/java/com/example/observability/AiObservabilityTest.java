package com.example.observability;

import io.micronaut.serde.ObjectMapper;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.sdk.OpenTelemetrySdk;
import io.opentelemetry.sdk.common.CompletableResultCode;
import io.opentelemetry.sdk.trace.SdkTracerProvider;
import io.opentelemetry.sdk.trace.data.SpanData;
import io.opentelemetry.sdk.trace.export.SimpleSpanProcessor;
import io.opentelemetry.sdk.trace.export.SpanExporter;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
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

        String response = observability.traceAgentTurn(
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
                                    () -> List.of("Hotel A"));
                            return "Found Hotel A";
                        }));

        assertEquals("Found Hotel A", response);
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
        assertTrue(tool.getEndEpochNanos() >= tool.getStartEpochNanos());
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
