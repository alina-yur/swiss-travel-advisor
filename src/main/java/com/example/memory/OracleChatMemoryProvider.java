package com.example.memory;

import dev.langchain4j.memory.ChatMemory;
import dev.langchain4j.memory.chat.ChatMemoryProvider;
import dev.langchain4j.memory.chat.MessageWindowChatMemory;
import io.micronaut.context.annotation.Primary;
import io.micronaut.langchain4j.store.memory.chat.MessageWindowChatMemoryConfiguration;
import jakarta.inject.Singleton;

@Primary
@Singleton
public class OracleChatMemoryProvider implements ChatMemoryProvider {
    private final OracleChatMemoryStore store;
    private final MessageWindowChatMemoryConfiguration configuration;

    public OracleChatMemoryProvider(
        OracleChatMemoryStore store,
        MessageWindowChatMemoryConfiguration configuration
    ) {
        this.store = store;
        this.configuration = configuration;
    }

    @Override
    public ChatMemory get(Object memoryId) {
        return MessageWindowChatMemory.builder()
            .id(memoryId)
            .maxMessages(configuration.getMaxMessages())
            .chatMemoryStore(store)
            .build();
    }
}
