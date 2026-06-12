package com.newsletter.fetcher;

import com.newsletter.model.Article;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class TwitterFetcherTest {

    @Test
    void parsesHtmlIntoArticles() {
        String html = """
            <html><body>
            <article data-testid="tweet">
              <div data-testid="tweetText">New blog post on scaling laws https://t.co/abc123</div>
              <time datetime="2026-06-12T08:30:00.000Z">Jun 12</time>
              <a href="/karpathy/status/123456789"></a>
            </article>
            </body></html>
            """;

        TwitterFetcher fetcher = new TwitterFetcher();
        List<Article> articles = fetcher.parseHtml(html, "karpathy");

        assertEquals(1, articles.size());
        assertTrue(articles.get(0).rawContent().contains("scaling laws"));
        assertEquals("@karpathy", articles.get(0).sourceName());
        assertEquals("twitter", articles.get(0).sourceType());
    }

    @Test
    void returnsEmptyOnParseFailure() {
        TwitterFetcher fetcher = new TwitterFetcher();
        List<Article> articles = fetcher.parseHtml("<html></html>", "karpathy");

        assertTrue(articles.isEmpty());
    }
}
