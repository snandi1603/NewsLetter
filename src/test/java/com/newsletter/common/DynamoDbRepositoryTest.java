package com.newsletter.common;

import com.newsletter.model.Article;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.dynamodb.model.AttributeValue;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class DynamoDbRepositoryTest {

    @Test
    void convertsArticleToItem() {
        DynamoDbRepository repository = new DynamoDbRepository(null, "articles", "digests", "feedback");

        Article article = new Article(
            "abc123", "2026-06-12", "Anthropic", "rss",
            "https://anthropic.com/blog/test", "Test Article",
            "Content here", null, 0, List.of(),
            "2026-06-12T10:00:00Z", "2026-06-12T06:00:00Z"
        );

        Map<String, AttributeValue> item = repository.articleToItem(article);

        assertEquals("abc123", item.get("article_id").s());
        assertEquals("2026-06-12", item.get("fetch_date").s());
        assertEquals("Anthropic", item.get("source_name").s());
        assertEquals("rss", item.get("source_type").s());
        assertEquals("https://anthropic.com/blog/test", item.get("original_url").s());
        assertEquals("Test Article", item.get("title").s());
        assertEquals("Content here", item.get("raw_content").s());
        assertEquals("0", item.get("relevance_score").n());
        assertEquals("2026-06-12T10:00:00Z", item.get("published_at").s());
        assertEquals("2026-06-12T06:00:00Z", item.get("created_at").s());
    }

    @Test
    void convertsArticleWithTagsToItem() {
        DynamoDbRepository repository = new DynamoDbRepository(null, "articles", "digests", "feedback");

        Article article = new Article(
            "def456", "2026-06-12", "OpenAI", "twitter",
            "https://twitter.com/openai/status/123", "AI Update",
            "New model release", "Summary of release", 85,
            List.of("AI", "ML", "GPT"),
            "2026-06-12T11:00:00Z", "2026-06-12T07:00:00Z"
        );

        Map<String, AttributeValue> item = repository.articleToItem(article);

        assertEquals("def456", item.get("article_id").s());
        assertEquals("Summary of release", item.get("summary").s());
        assertEquals("85", item.get("relevance_score").n());
        assertEquals(3, item.get("tags").ss().size());
        assertTrue(item.get("tags").ss().contains("AI"));
        assertTrue(item.get("tags").ss().contains("ML"));
        assertTrue(item.get("tags").ss().contains("GPT"));
    }

    @Test
    void handlesNullAndEmptyFields() {
        DynamoDbRepository repository = new DynamoDbRepository(null, "articles", "digests", "feedback");

        Article article = new Article(
            "ghi789", "2026-06-12", "Test", "rss",
            "https://test.com", "Title",
            null, null, 0, null,
            "2026-06-12T10:00:00Z", "2026-06-12T06:00:00Z"
        );

        Map<String, AttributeValue> item = repository.articleToItem(article);

        assertEquals("", item.get("raw_content").s());
        assertFalse(item.containsKey("summary"));
        assertFalse(item.containsKey("tags"));
    }
}
