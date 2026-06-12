package com.newsletter.curator;

import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Article;
import com.newsletter.model.Digest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class CuratorServiceTest {

    @Mock private DynamoDbRepository repository;
    @Mock private ClaudeClient claudeClient;
    @Mock private HtmlGenerator htmlGenerator;

    private CuratorService service;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        service = new CuratorService(repository, claudeClient, htmlGenerator);
    }

    @Test
    void curatesArticlesIntoDigest() throws Exception {
        List<Article> articles = List.of(
            new Article("id1", "2026-06-12", "Anthropic", "rss",
                "https://anthropic.com/post", "Post 1", "Content 1",
                null, 0, List.of(), "2026-06-12T10:00:00Z", "2026-06-12T06:00:00Z"),
            new Article("id2", "2026-06-12", "@karpathy", "twitter",
                "https://x.com/karpathy/1", "Post 2", "Content 2",
                null, 0, List.of(), "2026-06-12T08:00:00Z", "2026-06-12T06:00:00Z")
        );

        when(repository.getArticlesByDate("2026-06-12")).thenReturn(articles);
        when(claudeClient.curateArticles(articles)).thenReturn(List.of(
            new ClaudeClient.CurationResult("id1", 9, "Great post.", List.of("LLM")),
            new ClaudeClient.CurationResult("id2", 7, "Good insight.", List.of("research"))
        ));

        Digest digest = service.curate("2026-06-12");

        assertNotNull(digest);
        assertEquals("2026-06-12", digest.digestDate());
        assertEquals(2, digest.top5().size());
        assertEquals("id1", digest.top5().get(0).articleId());
        verify(repository).saveDigest(any());
    }

    @Test
    void handlesEmptyArticleDay() throws Exception {
        when(repository.getArticlesByDate("2026-06-12")).thenReturn(List.of());

        Digest digest = service.curate("2026-06-12");

        assertNotNull(digest);
        assertTrue(digest.top5().isEmpty());
        assertTrue(digest.more().isEmpty());
        verify(claudeClient, never()).curateArticles(any());
    }
}
