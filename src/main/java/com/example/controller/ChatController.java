package com.example.controller;

import com.example.service.SwissTravelAssistant;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Get;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.annotation.QueryValue;
import io.micronaut.serde.annotation.Serdeable;

@Controller("/api")
public class ChatController {
    private final SwissTravelAssistant assistant;

    public ChatController(SwissTravelAssistant assistant) {
        this.assistant = assistant;
    }

    @Serdeable
    public record ChatRequest(String message) {}

    @Post(uri = "/chat", consumes = MediaType.APPLICATION_JSON, produces = MediaType.TEXT_PLAIN)
    public String chat(@Body ChatRequest req) {
        return assistant.chat(req.message());
    }

    @Get(uri = "/chat", produces = MediaType.TEXT_PLAIN)
    public String chatGet(@QueryValue("q") String query) {
        return assistant.chat(query);
    }
}
