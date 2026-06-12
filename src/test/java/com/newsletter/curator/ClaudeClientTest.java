package com.newsletter.curator;

import com.newsletter.model.Article;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class ClaudeClientTest {

    @Test
    void buildsCurationPromptCorrectly() {
        List<Article> articles = List.of(
            new Article("id1", "2026-06-12", "Anthropic", "rss",
                "https://anthropic.com/blog/test", "Claude 4 Released",
                "We are excited to announce Claude 4.", null, 0, List.of(),
                "2026-06-12T10:00:00Z", "2026-06-12T06:00:00Z"),
            new Article("id2", "2026-06-12", "@karpathy", "twitter",
                "https://x.com/karpathy/1", "Scaling laws insight...",
                "Key insight on compute-optimal training.", null, 0, List.of(),
                "2026-06-12T08:00:00Z", "2026-06-12T06:00:00Z")
        );

        ClaudeClient client = new ClaudeClient("fake-key");
        String prompt = client.buildCurationPrompt(articles);

        assertTrue(prompt.contains("Claude 4 Released"));
        assertTrue(prompt.contains("Scaling laws insight"));
        assertTrue(prompt.contains("article_id"));
        assertTrue(prompt.contains("Score each 1-10"));
    }

    @Test
    void parsesCurationResponseJson() throws Exception {
        String json = """
            [
              {"article_id": "id1", "score": 9, "summary": "Major release.", "tags": ["LLM", "release"]},
              {"article_id": "id2", "score": 7, "summary": "Training insight.", "tags": ["research"]}
            ]
            """;

        ClaudeClient client = new ClaudeClient("fake-key");
        var results = client.parseCurationResponse(json);

        assertEquals(2, results.size());
        assertEquals(9, results.get(0).score());
        assertEquals("Major release.", results.get(0).summary());
        assertEquals(List.of("LLM", "release"), results.get(0).tags());
    }
}
