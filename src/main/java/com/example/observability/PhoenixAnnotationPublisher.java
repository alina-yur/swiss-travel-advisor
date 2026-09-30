package com.example.observability;

import io.micronaut.context.annotation.Value;
import io.micronaut.serde.ObjectMapper;
import jakarta.inject.Singleton;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

/** Publishes lightweight deterministic evaluations to Phoenix as span annotations. */
@Singleton
public final class PhoenixAnnotationPublisher {
    private static final Logger LOG = LoggerFactory.getLogger(PhoenixAnnotationPublisher.class);

    private final ObjectMapper objectMapper;
    private final HttpClient httpClient;
    private final URI annotationsUri;
    private final boolean enabled;
    private final String apiKey;

    public PhoenixAnnotationPublisher(
            ObjectMapper objectMapper,
            @Value("${app.ai.tracing.annotations.enabled:false}") boolean enabled,
            @Value("${app.ai.tracing.phoenix-base-url:http://localhost:6006}") String phoenixBaseUrl,
            @Value("${app.ai.tracing.phoenix-api-key:}") String apiKey) {
        this.objectMapper = objectMapper;
        this.enabled = enabled;
        this.apiKey = apiKey;
        this.annotationsUri = URI.create(stripTrailingSlash(phoenixBaseUrl) + "/v1/span_annotations?sync=false");
        this.httpClient = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    }

    public void publish(String spanId, List<Evaluation> evaluations) {
        if (!enabled || spanId == null || spanId.isBlank() || evaluations.isEmpty()) {
            return;
        }

        // OTLP spans are exported in batches. Give Phoenix enough time to
        // persist the span before attempting the annotation, especially when
        // the application is under light load during a live demo.
        CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)
                .execute(() -> send(spanId, evaluations, "CODE", "conference-demo", true));
    }

    public void publishUserFeedback(String spanId, boolean helpful) {
        if (!enabled || spanId == null || spanId.isBlank()) {
            return;
        }
        Evaluation feedback = new Evaluation(
                "user_feedback",
                helpful ? "helpful" : "not_helpful",
                helpful ? 1.0 : 0.0,
                helpful ? "The user marked this response as helpful."
                        : "The user marked this response as not helpful.");
        CompletableFuture.delayedExecutor(2, TimeUnit.SECONDS)
                .execute(() -> send(spanId, List.of(feedback), "HUMAN", "travel-advisor-ui", true));
    }

    private void send(
            String spanId,
            List<Evaluation> evaluations,
            String annotatorKind,
            String identifier,
            boolean retry) {
        try {
            List<Map<String, Object>> annotations = evaluations.stream()
                    .map(evaluation -> Map.<String, Object>of(
                            "name", evaluation.name(),
                            "annotator_kind", annotatorKind,
                            "span_id", spanId,
                            "result", Map.of(
                                    "label", evaluation.label(),
                                    "score", evaluation.score(),
                                    "explanation", evaluation.explanation()),
                            "metadata", Map.of("source", "swiss-travel-advisor"),
                            "identifier", identifier))
                    .toList();
            String body = objectMapper.writeValueAsString(Map.of("data", annotations));
            HttpRequest.Builder request = HttpRequest.newBuilder(annotationsUri)
                    .timeout(Duration.ofSeconds(3))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body));
            if (apiKey != null && !apiKey.isBlank()) {
                request.header("api_key", apiKey);
            }

            HttpResponse<Void> response = httpClient.send(request.build(), HttpResponse.BodyHandlers.discarding());
            if (response.statusCode() >= 300 && retry) {
                CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)
                        .execute(() -> send(spanId, evaluations, annotatorKind, identifier, false));
            } else if (response.statusCode() >= 300) {
                LOG.debug("Could not publish Phoenix annotations for span {}: HTTP {}", spanId, response.statusCode());
            }
        } catch (IOException | InterruptedException | RuntimeException error) {
            if (error instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            if (retry) {
                CompletableFuture.delayedExecutor(5, TimeUnit.SECONDS)
                        .execute(() -> send(spanId, evaluations, annotatorKind, identifier, false));
            } else {
                LOG.debug("Could not publish Phoenix annotations for span {}", spanId, error);
            }
        }
    }

    private static String stripTrailingSlash(String value) {
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }

    public record Evaluation(String name, String label, double score, String explanation) {
    }
}
