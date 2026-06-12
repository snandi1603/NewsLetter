package com.newsletter.integration;

import com.newsletter.curator.ClaudeClient;
import com.newsletter.curator.CuratorService;
import com.newsletter.curator.HtmlGenerator;
import com.newsletter.common.DynamoDbRepository;
import com.newsletter.fetcher.FetcherService;
import com.newsletter.fetcher.RssFetcher;
import com.newsletter.fetcher.TwitterFetcher;
import com.newsletter.model.Article;
import com.newsletter.model.Digest;
import com.newsletter.model.SourceConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.mockito.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EndToEndTest {

    @Mock private DynamoDbRepository repository;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
    }

    @Test
    void fullPipelineFromFetchToCuration() throws Exception {
        RssFetcher rssFetcher = mock(RssFetcher.class);
        TwitterFetcher twitterFetcher = mock(TwitterFetcher.class);

        Article a1 = new Article("id1", "2026-06-12", "Anthropic", "rss",
            "https://anthropic.com/blog/post1", "Claude Gets Smarter",
            "Big improvements to Claude's reasoning.",
            null, 0, List.of(), "2026-06-12T10:00:00Z", "2026-06-12T06:00:00Z");
        Article a2 = new Article("id2", "2026-06-12", "@karpathy", "twitter",
            "https://x.com/karpathy/123", "New scaling paper...",
            "Published new findings on neural scaling laws.",
            null, 0, List.of(), "2026-06-12T08:00:00Z", "2026-06-12T06:00:00Z");

        SourceConfig config = new SourceConfig(
            List.of(new SourceConfig.RssSource("Anthropic", "https://anthropic.com/rss", "company_blog")),
            List.of(new SourceConfig.TwitterSource("karpathy")),
            List.of(), List.of()
        );

        when(rssFetcher.fetchFromUrl(anyString(), eq("Anthropic"), eq("rss"))).thenReturn(List.of(a1));
        when(twitterFetcher.fetchTweets("karpathy")).thenReturn(List.of(a2));
        when(repository.exists(anyString(), anyString())).thenReturn(false);

        // Run fetcher
        FetcherService fetcherService = new FetcherService(repository, rssFetcher, twitterFetcher);
        int saved = fetcherService.fetchAll(config);
        assertEquals(2, saved);

        // Simulate curator
        when(repository.getArticlesByDate("2026-06-12")).thenReturn(List.of(a1, a2));

        ClaudeClient claudeClient = mock(ClaudeClient.class);
        when(claudeClient.curateArticles(anyList())).thenReturn(List.of(
            new ClaudeClient.CurationResult("id1", 9, "Major Claude upgrade.", List.of("LLM")),
            new ClaudeClient.CurationResult("id2", 7, "Scaling law findings.", List.of("research"))
        ));

        HtmlGenerator htmlGenerator = new HtmlGenerator("https://api.example.com");
        CuratorService curatorService = new CuratorService(repository, claudeClient, htmlGenerator);
        Digest digest = curatorService.curate("2026-06-12");

        assertNotNull(digest);
        assertEquals(2, digest.top5().size() + digest.more().size());
        assertEquals("id1", digest.top5().get(0).articleId());

        // Generate HTML
        String html = curatorService.generateHtml(digest);
        assertTrue(html.contains("Claude Gets Smarter"));
        assertTrue(html.contains("https://anthropic.com/blog/post1"));
        assertTrue(html.contains("feedback"));
    }
}
