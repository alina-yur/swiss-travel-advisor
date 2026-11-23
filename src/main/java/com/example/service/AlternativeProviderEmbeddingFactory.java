package com.example.service;

import dev.langchain4j.model.mistralai.MistralAiEmbeddingModel;
import dev.langchain4j.model.ollama.OllamaEmbeddingModel;
import dev.langchain4j.model.openai.OpenAiEmbeddingModel;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import jakarta.inject.Singleton;

/** Keeps Oracle's stored vectors compatible when the chat provider changes. */
@Factory
public class AlternativeProviderEmbeddingFactory {
    @Singleton
    @Requires(env = "mistral")
    @Replaces(bean = MistralAiEmbeddingModel.class)
    OpenAiEmbeddingModel hostedEmbeddingModel(
            @Value("${langchain4j.open-ai.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.embedding-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.embedding-model.model-name}") String modelName) {
        return create(apiKey, baseUrl, modelName);
    }

    @Singleton
    @Requires(env = "mistral-local")
    @Replaces(bean = OllamaEmbeddingModel.class)
    OpenAiEmbeddingModel localEmbeddingModel(
            @Value("${langchain4j.open-ai.api-key}") String apiKey,
            @Value("${langchain4j.open-ai.embedding-model.base-url}") String baseUrl,
            @Value("${langchain4j.open-ai.embedding-model.model-name}") String modelName) {
        return create(apiKey, baseUrl, modelName);
    }

    private static OpenAiEmbeddingModel create(String apiKey, String baseUrl, String modelName) {
        return OpenAiEmbeddingModel.builder().apiKey(apiKey).baseUrl(baseUrl)
                .modelName(modelName).build();
    }
}
