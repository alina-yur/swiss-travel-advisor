package com.example.controller;

import com.example.observability.PhoenixAnnotationPublisher;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.exceptions.HttpStatusException;
import io.micronaut.serde.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class FeedbackControllerTest {

    private final FeedbackController controller = new FeedbackController(
            new PhoenixAnnotationPublisher(ObjectMapper.getDefault(), false, "http://localhost:6006", ""));

    @Test
    void acceptsFeedbackForAnAgentSpan() {
        var response = controller.submit(new FeedbackController.FeedbackRequest("0123456789abcdef", true));

        assertEquals(HttpStatus.ACCEPTED, response.getStatus());
        assertEquals("accepted", response.body().status());
    }

    @Test
    void rejectsInvalidSpanIds() {
        HttpStatusException error = assertThrows(HttpStatusException.class,
                () -> controller.submit(new FeedbackController.FeedbackRequest("not-a-span", false)));

        assertEquals(HttpStatus.BAD_REQUEST, error.getStatus());
    }
}
