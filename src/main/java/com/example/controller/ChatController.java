package com.example.controller;

import com.example.service.SwissTravelAssistant;
import io.micronaut.http.MediaType;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.serde.annotation.Serdeable;

import java.util.UUID;

@Controller("/api")
public class ChatController {
    private static final int LOCK_STRIPES = 64;

    private final SwissTravelAssistant assistant;
    private final Object[] conversationLocks = new Object[LOCK_STRIPES];

    public ChatController(SwissTravelAssistant assistant) {
        this.assistant = assistant;
        for (int i = 0; i < conversationLocks.length; i++) {
            conversationLocks[i] = new Object();
        }
    }

    @Serdeable
    public record ChatRequest(String message, @Nullable String conversationId) {}

    @Serdeable
    public record ChatReply(String conversationId, String message) {}

    @Post(uri = "/chat", consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public ChatReply chat(@Body ChatRequest req) {
        return chat(req.message(), req.conversationId());
    }

    @Get(uri = "/chat", produces = MediaType.APPLICATION_JSON)
    public ChatReply chatGet(
        @QueryValue("q") String query,
        @Nullable @QueryValue("conversationId") String conversationId
    ) {
        return chat(query, conversationId);
    }

    private ChatReply chat(String message, @Nullable String requestedConversationId) {
        if (message == null || message.isBlank()) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "message must not be blank");
        }

        String conversationId = normalizeConversationId(requestedConversationId);
        Object lock = conversationLocks[Math.floorMod(conversationId.hashCode(), conversationLocks.length)];
        synchronized (lock) {
            return new ChatReply(conversationId, assistant.chat(conversationId, message));
        }
    }

    private String normalizeConversationId(@Nullable String requestedConversationId) {
        if (requestedConversationId == null || requestedConversationId.isBlank()) {
            return UUID.randomUUID().toString();
        }

        try {
            UUID id = UUID.fromString(requestedConversationId);
            if (!id.toString().equalsIgnoreCase(requestedConversationId)) {
                throw new IllegalArgumentException("Non-canonical UUID");
            }
            return id.toString();
        } catch (IllegalArgumentException e) {
            throw new HttpStatusException(HttpStatus.BAD_REQUEST, "conversationId must be a UUID");
        }
    }
}
