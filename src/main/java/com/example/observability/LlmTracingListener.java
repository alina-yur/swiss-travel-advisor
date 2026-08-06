package com.example.observability;

import dev.langchain4j.data.message.ChatMessageSerializer;
import dev.langchain4j.model.chat.listener.ChatModelErrorContext;
import dev.langchain4j.model.chat.listener.ChatModelListener;
import dev.langchain4j.model.chat.listener.ChatModelRequestContext;
import dev.langchain4j.model.chat.listener.ChatModelResponseContext;
import dev.langchain4j.model.chat.request.ChatRequest;
import dev.langchain4j.model.chat.response.ChatResponse;
import dev.langchain4j.model.output.TokenUsage;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.api.common.AttributeKey;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanKind;
import io.opentelemetry.api.trace.StatusCode;
import io.opentelemetry.api.trace.Tracer;
import jakarta.inject.Singleton;

import java.util.List;

/**
 * Exports LangChain4j chat calls as OpenTelemetry/OpenInference-compatible
 * spans. Phoenix recognizes these attributes and renders the spans as LLM
 * calls instead of generic HTTP operations.
 */
@Singleton
@Requires(property = "app.ai.tracing.enabled", value = "true")
public final class LlmTracingListener implements ChatModelListener {

    private static final String SPAN_ATTRIBUTE = LlmTracingListener.class.getName() + ".span";
    private static final String JSON_MIME_TYPE = "application/json";

    private final Tracer tracer;
    private final boolean includeContent;
    private final AiObservability observability;

    public LlmTracingListener(
            OpenTelemetry openTelemetry,
            AiObservability observability,
            @Value("${app.ai.tracing.include-content:false}") boolean includeContent) {
        this.tracer = openTelemetry.getTracer("com.example.swiss-travel-advisor.llm");
        this.observability = observability;
        this.includeContent = includeContent;
    }

    @Override
    public void onRequest(ChatModelRequestContext context) {
        ChatRequest request = context.chatRequest();
        String model = valueOrUnknown(request.modelName());
        Span span = tracer.spanBuilder("chat " + model)
                .setSpanKind(SpanKind.CLIENT)
                .setAttribute("openinference.span.kind", "LLM")
                .setAttribute("gen_ai.operation.name", "chat")
                .setAttribute("gen_ai.provider.name", "openai")
                .setAttribute("gen_ai.request.model", model)
                .setAttribute("server.address", "api.openai.com")
                .startSpan();

        String sessionId = observability.currentSessionId();
        if (sessionId != null) {
            span.setAttribute("session.id", sessionId);
        }

        setIfPresent(span, "gen_ai.request.temperature", request.temperature());
        setIfPresent(span, "gen_ai.request.top_p", request.topP());
        setIfPresent(span, "gen_ai.request.max_tokens", request.maxOutputTokens());
        span.setAttribute("llm.tools.count",
                request.toolSpecifications() == null ? 0 : request.toolSpecifications().size());

        if (includeContent) {
            String input = ChatMessageSerializer.messagesToJson(request.messages());
            span.setAttribute("input.value", input);
            span.setAttribute("input.mime_type", JSON_MIME_TYPE);
            span.setAttribute("gen_ai.input.messages", input);
        }

        context.attributes().put(SPAN_ATTRIBUTE, span);
    }

    @Override
    public void onResponse(ChatModelResponseContext context) {
        Span span = removeSpan(context.attributes().remove(SPAN_ATTRIBUTE));
        if (span == null) {
            return;
        }

        try {
            ChatResponse response = context.chatResponse();
            setIfPresent(span, "gen_ai.response.id", response.id());
            setIfPresent(span, "gen_ai.response.model", response.modelName());
            if (response.finishReason() != null) {
                span.setAttribute(AttributeKey.stringArrayKey("gen_ai.response.finish_reasons"),
                        List.of(response.finishReason().name()));
            }
            recordTokenUsage(span, response.tokenUsage());

            if (includeContent && response.aiMessage() != null) {
                String output = ChatMessageSerializer.messageToJson(response.aiMessage());
                span.setAttribute("output.value", output);
                span.setAttribute("output.mime_type", JSON_MIME_TYPE);
                span.setAttribute("gen_ai.output.messages", output);
            }
            span.setStatus(StatusCode.OK);
        } finally {
            span.end();
        }
    }

    @Override
    public void onError(ChatModelErrorContext context) {
        Span span = removeSpan(context.attributes().remove(SPAN_ATTRIBUTE));
        if (span == null) {
            return;
        }

        try {
            span.recordException(context.error());
            span.setStatus(StatusCode.ERROR, valueOrUnknown(context.error().getMessage()));
            span.setAttribute("error.type", context.error().getClass().getName());
        } finally {
            span.end();
        }
    }

    private static void recordTokenUsage(Span span, TokenUsage usage) {
        if (usage == null) {
            return;
        }
        setIfPresent(span, "gen_ai.usage.input_tokens", usage.inputTokenCount());
        setIfPresent(span, "gen_ai.usage.output_tokens", usage.outputTokenCount());
        setIfPresent(span, "llm.token_count.prompt", usage.inputTokenCount());
        setIfPresent(span, "llm.token_count.completion", usage.outputTokenCount());
        setIfPresent(span, "llm.token_count.total", usage.totalTokenCount());
    }

    private static Span removeSpan(Object value) {
        return value instanceof Span span ? span : null;
    }

    private static String valueOrUnknown(String value) {
        return value == null || value.isBlank() ? "unknown" : value;
    }

    private static void setIfPresent(Span span, String name, String value) {
        if (value != null && !value.isBlank()) {
            span.setAttribute(name, value);
        }
    }

    private static void setIfPresent(Span span, String name, Number value) {
        if (value instanceof Integer integer) {
            span.setAttribute(name, integer.longValue());
        } else if (value instanceof Long longValue) {
            span.setAttribute(name, longValue);
        } else if (value != null) {
            span.setAttribute(name, value.doubleValue());
        }
    }
}
