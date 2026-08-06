package com.example.observability;

import dev.langchain4j.agent.tool.ToolExecutionRequest;
import dev.langchain4j.observability.api.event.ToolExecutedEvent;
import dev.langchain4j.observability.api.listener.ToolExecutedEventListener;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.Attributes;
import io.opentelemetry.api.trace.Span;
import jakarta.inject.Singleton;

/** Adds the LangChain4j call ID and result event to the active agent trace. */
@Singleton
@Requires(property = "app.ai.tracing.enabled", value = "true")
public final class ToolTracingListener implements ToolExecutedEventListener {
    private final boolean includeContent;

    public ToolTracingListener(
            OpenTelemetry openTelemetry,
            @Value("${app.ai.tracing.include-content:false}") boolean includeContent) {
        // Keep OpenTelemetry in the signature so Micronaut only creates this listener
        // when the tracing infrastructure is available.
        openTelemetry.getTracer("com.example.swiss-travel-advisor.tools.events");
        this.includeContent = includeContent;
    }

    @Override
    public void onEvent(ToolExecutedEvent event) {
        ToolExecutionRequest request = event.request();
        var attributes = Attributes.builder()
                .put("tool.name", request.name());
        if (request.id() != null) {
            attributes.put("tool.call.id", request.id());
        }
        if (includeContent && request.arguments() != null) {
            attributes.put("tool.parameters", request.arguments());
        }
        if (includeContent && event.resultText() != null) {
            attributes.put("tool.result", event.resultText());
        }
        Span.current().addEvent("tool.executed", attributes.build());
    }
}
