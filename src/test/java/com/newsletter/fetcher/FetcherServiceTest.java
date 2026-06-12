package com.newsletter.fetcher;

import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Article;
import com.newsletter.model.SourceConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class FetcherServiceTest {

    @Mock private DynamoDbRepository repository;
    @Mock private RssFetcher rssFetcher;
    @Mock private TwitterFetcher twitterFetcher;

    private FetcherService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new FetcherService(repository, rssFetcher, twitterFetcher);
    }

    @Test
    void fetchesFromAllSourcesAndSavesNew() {
        SourceConfig config = new SourceConfig(
            List.of(new SourceConfig.RssSource("Anthropic", "https://anthropic.com/rss", "company_blog")),
            List.of(new SourceConfig.TwitterSource("karpathy")),
            List.of(),
            List.of()
        );

        Article rssArticle = new Article("id1", "2026-06-12", "Anthropic", "rss",
            "https://anthropic.com/blog/post", "Post Title", "Content",
            null, 0, List.of(), "2026-06-12T10:00:00Z", "2026-06-12T06:00:00Z");
        Article twitterArticle = new Article("id2", "2026-06-12", "@karpathy", "twitter",
            "https://x.com/karpathy/status/1", "Tweet...", "Tweet content",
            null, 0, List.of(), "2026-06-12T08:00:00Z", "2026-06-12T06:00:00Z");

        when(rssFetcher.fetchFromUrl(anyString(), eq("Anthropic"), eq("rss")))
            .thenReturn(List.of(rssArticle));
        when(twitterFetcher.fetchTweets("karpathy"))
            .thenReturn(List.of(twitterArticle));
        when(repository.exists(anyString(), anyString())).thenReturn(false);

        int saved = service.fetchAll(config);

        assertEquals(2, saved);
        verify(repository, times(2)).saveArticle(any());
    }

    @Test
    void skipsExistingArticles() {
        SourceConfig config = new SourceConfig(
            List.of(new SourceConfig.RssSource("Anthropic", "https://anthropic.com/rss", "company_blog")),
            List.of(), List.of(), List.of()
        );

        Article article = new Article("id1", "2026-06-12", "Anthropic", "rss",
            "https://anthropic.com/blog/post", "Post", "Content",
            null, 0, List.of(), "2026-06-12T10:00:00Z", "2026-06-12T06:00:00Z");

        when(rssFetcher.fetchFromUrl(anyString(), anyString(), anyString()))
            .thenReturn(List.of(article));
        when(repository.exists("id1", "2026-06-12")).thenReturn(true);

        int saved = service.fetchAll(config);

        assertEquals(0, saved);
        verify(repository, never()).saveArticle(any());
    }
}
