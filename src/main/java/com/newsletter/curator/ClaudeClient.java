package com.newsletter.curator;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.newsletter.model.Article;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;

public class ClaudeClient {

    private final String apiKey;
    private final ObjectMapper mapper = new ObjectMapper();
    private static final String API_URL = "https://api.anthropic.com/v1/messages";

    public ClaudeClient(String apiKey) {
        this.apiKey = apiKey;
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CurationResult(
        @JsonProperty("article_id") String articleId,
        int score,
        String summary,
        List<String> tags
    ) {}

    public List<CurationResult> curateArticles(List<Article> articles) throws Exception {
        String prompt = buildCurationPrompt(articles);

        HttpClient client = HttpClient.newHttpClient();
        String requestBody = mapper.writeValueAsString(Map.of(
            "model", "claude-sonnet-4-6-20250514",
            "max_tokens", 4096,
            "messages", List.of(Map.of("role", "user", "content", prompt))
        ));

        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(API_URL))
            .header("Content-Type", "application/json")
            .header("x-api-key", apiKey)
            .header("anthropic-version", "2023-06-01")
            .POST(HttpRequest.BodyPublishers.ofString(requestBody))
            .build();

        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        var responseBody = mapper.readTree(response.body());
        String content = responseBody.get("content").get(0).get("text").asText();
        return parseCurationResponse(extractJson(content));
    }

    public String buildCurationPrompt(List<Article> articles) {
        StringBuilder sb = new StringBuilder();
        sb.append("""
            You are an AI research news curator. Given these articles, rank them by:
            1. Significance (breakthrough, major release, or important announcement vs. minor update)
            2. Novelty (new information vs. rehash of known topics)
            3. Breadth of impact (affects the whole AI field vs. niche use case)

            Score each 1-10 and provide a 2-3 sentence summary.
            Ensure diversity of sources in the top 5 when possible.

            Return ONLY a JSON array: [{ "article_id": "...", "score": N, "summary": "...", "tags": ["..."] }]

            Articles:
            """);

        for (Article article : articles) {
            sb.append("\n---\n");
            sb.append("article_id: ").append(article.articleId()).append("\n");
            sb.append("title: ").append(article.title()).append("\n");
            sb.append("source: ").append(article.sourceName()).append("\n");
            sb.append("content: ").append(truncate(article.rawContent(), 2000)).append("\n");
        }
        return sb.toString();
    }

    public List<CurationResult> parseCurationResponse(String json) throws Exception {
        return mapper.readValue(json,
            mapper.getTypeFactory().constructCollectionType(List.class, CurationResult.class));
    }

    private String extractJson(String text) {
        int start = text.indexOf('[');
        int end = text.lastIndexOf(']');
        if (start >= 0 && end > start) {
            return text.substring(start, end + 1);
        }
        return text;
    }

    private String truncate(String text, int maxChars) {
        if (text == null) return "";
        return text.length() > maxChars ? text.substring(0, maxChars) : text;
    }
}
