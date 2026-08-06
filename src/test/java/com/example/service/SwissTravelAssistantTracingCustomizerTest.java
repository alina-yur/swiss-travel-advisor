package com.example.service;

import com.example.observability.ToolTracingListener;
import dev.langchain4j.service.AiServices;
import io.micronaut.langchain4j.aiservices.AiServiceCreationContext;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

class SwissTravelAssistantTracingCustomizerTest {

    @Test
    void registersToolTracingWithoutReplacingConfiguredTools() {
        ToolTracingListener listener = mock(ToolTracingListener.class);
        @SuppressWarnings("unchecked")
        AiServices<SwissTravelAssistant> aiServices = mock(AiServices.class);
        SwissTravelAssistantTracingCustomizer customizer =
                new SwissTravelAssistantTracingCustomizer(listener);

        customizer.customize(new AiServiceCreationContext<>(null, aiServices));

        verify(aiServices).registerListener(listener);
    }
}
