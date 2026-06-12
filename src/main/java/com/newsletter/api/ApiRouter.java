package com.newsletter.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Digest;
import com.newsletter.model.Feedback;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class ApiRouter {

    private final DynamoDbRepository repository;
    private final ObjectMapper mapper = new ObjectMapper();

    public ApiRouter(DynamoDbRepository repository) {
        this.repository = repository;
    }

    public record Response(int statusCode, String body, Map<String, String> headers) {
        public Response(int statusCode, String body) {
            this(statusCode, body, Map.of(
                "Content-Type", "application/json",
                "Access-Control-Allow-Origin", "*",
                "Access-Control-Allow-Methods", "GET, POST, OPTIONS",
                "Access-Control-Allow-Headers", "Content-Type"
            ));
        }
    }

    public Response handleGet(String path, Map<String, String> params) {
        try {
            return switch (path) {
                case "/digest" -> getDigest(params.getOrDefault("date", ""));
                case "/sources" -> getSources();
                default -> new Response(404, "{\"error\": \"Not found\"}");
            };
        } catch (Exception e) {
            return new Response(500, "{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    public Response handlePost(String path, String body) {
        try {
            return switch (path) {
                case "/feedback" -> postFeedback(body);
                default -> new Response(404, "{\"error\": \"Not found\"}");
            };
        } catch (Exception e) {
            return new Response(500, "{\"error\": \"" + e.getMessage() + "\"}");
        }
    }

    private Response getDigest(String date) throws Exception {
        Optional<Digest> digest = repository.getDigest(date);
        if (digest.isEmpty()) {
            return new Response(404, "{\"error\": \"No digest for date: " + date + "\"}");
        }
        return new Response(200, mapper.writeValueAsString(digest.get()));
    }

    private Response getSources() {
        return new Response(200, "{\"message\": \"sources endpoint\"}");
    }

    private Response postFeedback(String body) throws Exception {
        @SuppressWarnings("unchecked")
        Map<String, String> data = mapper.readValue(body, Map.class);
        Feedback feedback = new Feedback(
            data.get("article_id"),
            data.get("digest_date"),
            data.get("feedback"),
            "",
            List.of(),
            Instant.now().toString()
        );
        repository.saveFeedback(feedback);
        return new Response(200, "{\"status\": \"saved\"}");
    }
}
