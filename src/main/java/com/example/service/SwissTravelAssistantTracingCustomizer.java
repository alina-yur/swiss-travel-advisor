package com.example.service;

import com.example.observability.ToolTracingListener;
import io.micronaut.context.annotation.Requires;
import io.micronaut.langchain4j.aiservices.AiServiceCreationContext;
import io.micronaut.langchain4j.aiservices.AiServiceCustomizer;
import jakarta.inject.Singleton;

/** Registers proxy-safe LangChain4j tool tracing when AI tracing is enabled. */
@Singleton
@Requires(property = "app.ai.tracing.enabled", value = "true")
public final class SwissTravelAssistantTracingCustomizer implements AiServiceCustomizer<SwissTravelAssistant> {

    private final ToolTracingListener toolTracingListener;

    public SwissTravelAssistantTracingCustomizer(ToolTracingListener toolTracingListener) {
        this.toolTracingListener = toolTracingListener;
    }

    @Override
    public void customize(AiServiceCreationContext<SwissTravelAssistant> context) {
        context.aiServices().registerListener(toolTracingListener);
    }
}
