package com.example.service;

import com.example.observability.AiObservability;
import dev.langchain4j.data.embedding.Embedding;
import dev.langchain4j.model.embedding.EmbeddingModel;
import jakarta.inject.Singleton;

@Singleton
public class EmbeddingService {
    private final EmbeddingModel embeddingModel;
    private final AiObservability observability;

    public EmbeddingService(EmbeddingModel embeddingModel, AiObservability observability) {
        this.embeddingModel = embeddingModel;
        this.observability = observability;
    }

    public float[] generateEmbedding(String text) {
        return observability.traceEmbedding(text, () -> {
            Embedding embedding = embeddingModel.embed(text).content();
            return embedding.vector();
        });
    }
}
