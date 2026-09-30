package com.example.controller;

import com.example.observability.PhoenixAnnotationPublisher;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.serde.annotation.Serdeable;

import java.util.regex.Pattern;

import static io.micronaut.http.HttpStatus.BAD_REQUEST;

@Controller("/api/feedback")
public final class FeedbackController {
    private static final Pattern SPAN_ID = Pattern.compile("[0-9a-fA-F]{16}");

    private final PhoenixAnnotationPublisher annotationPublisher;

    public FeedbackController(PhoenixAnnotationPublisher annotationPublisher) {
        this.annotationPublisher = annotationPublisher;
    }

    @Serdeable
    public record FeedbackRequest(String spanId, Boolean helpful) {
    }

    @Serdeable
    public record FeedbackReply(String status) {
    }

    @Post(consumes = MediaType.APPLICATION_JSON, produces = MediaType.APPLICATION_JSON)
    public HttpResponse<FeedbackReply> submit(@Body FeedbackRequest request) {
        if (request == null || request.spanId() == null || !SPAN_ID.matcher(request.spanId()).matches()) {
            throw new HttpStatusException(BAD_REQUEST, "spanId must be a 16-character hexadecimal OpenTelemetry span ID");
        }
        if (request.helpful() == null) {
            throw new HttpStatusException(BAD_REQUEST, "helpful must be true or false");
        }

        annotationPublisher.publishUserFeedback(request.spanId().toLowerCase(), request.helpful());
        return HttpResponse.<FeedbackReply>status(HttpStatus.ACCEPTED)
                .body(new FeedbackReply("accepted"));
    }
}
