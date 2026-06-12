package com.newsletter.fetcher;

import com.newsletter.model.Article;
import org.junit.jupiter.api.Test;
import java.io.InputStream;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class RssFetcherTest {

    @Test
    void parsesRssFeedIntoArticles() throws Exception {
        InputStream rssStream = getClass().getResourceAsStream("/sample-rss.xml");
        RssFetcher fetcher = new RssFetcher();

        List<Article> articles = fetcher.parseFromStream(rssStream, "Anthropic", "rss");

        assertEquals(2, articles.size());
        assertEquals("Introducing Claude 4", articles.get(0).title());
        assertEquals("https://anthropic.com/blog/claude-4", articles.get(0).originalUrl());
        assertEquals("Anthropic", articles.get(0).sourceName());
        assertEquals("rss", articles.get(0).sourceType());
        assertNotNull(articles.get(0).articleId());
    }

    @Test
    void generatesConsistentArticleId() throws Exception {
        InputStream rssStream = getClass().getResourceAsStream("/sample-rss.xml");
        RssFetcher fetcher = new RssFetcher();

        List<Article> first = fetcher.parseFromStream(rssStream, "Anthropic", "rss");
        rssStream = getClass().getResourceAsStream("/sample-rss.xml");
        List<Article> second = fetcher.parseFromStream(rssStream, "Anthropic", "rss");

        assertEquals(first.get(0).articleId(), second.get(0).articleId());
    }
}
