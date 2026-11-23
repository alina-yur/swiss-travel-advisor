package com.example;

import dev.langchain4j.model.chat.ChatModel;
import dev.langchain4j.model.embedding.EmbeddingModel;
import dev.langchain4j.model.mistralai.MistralAiChatModel;
import dev.langchain4j.model.ollama.OllamaChatModel;
import dev.langchain4j.model.openai.OpenAiChatModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.micronaut.context.ApplicationContext;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Properties;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

class ChatModelWiringTest {
    @Test
    void defaultProvider() {
        assertWiring(null, OpenAiChatModel.class);
    }

    @Test
    void hostedMistralProvider() {
        assertWiring("mistral", MistralAiChatModel.class);
    }

    @Test
    void localMistralProvider() {
        assertWiring("mistral-local", OllamaChatModel.class);
    }

    private static void loadProperties(Map<String, Object> target, String resource) {
        try (InputStream input = ChatModelWiringTest.class.getClassLoader().getResourceAsStream(resource)) {
            Properties loaded = new Properties();
            loaded.load(input);
            loaded.forEach((key, value) -> target.put((String) key, value));
        } catch (IOException error) {
            throw new IllegalStateException(error);
        }
    }

    private void assertWiring(String environment, Class<? extends ChatModel> expected) {
        Map<String, Object> properties = new HashMap<>();
        loadProperties(properties, "application.properties");
        if (environment != null) {
            loadProperties(properties, "application-" + environment + ".properties");
        }
        properties.keySet().removeIf(key -> key.startsWith("datasources."));
        properties.put("langchain4j.open-ai.api-key", "unused-test-value");
        properties.put("langchain4j.mistral-ai.api-key", "unused-test-value");
        properties.put("flyway.datasources.default.enabled", false);
        properties.put("app.ai.tracing.enabled", false);
        io.micronaut.context.ApplicationContextBuilder builder = ApplicationContext.builder().deduceEnvironment(false)
                .enableDefaultPropertySources(false).properties(properties);
        builder.environments(environment == null ? "wiring-default" : environment);
        try (ApplicationContext context = builder.start()) {
            assertEquals(1, context.getBeansOfType(ChatModel.class).size());
            assertInstanceOf(expected, context.getBean(ChatModel.class));
            assertEquals(1, context.getBeansOfType(EmbeddingModel.class).size());
            assertInstanceOf(OpenAiEmbeddingModel.class, context.getBean(EmbeddingModel.class));
        }
    }
}
