package com.newsletter.api;

import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Digest;
import com.newsletter.model.Feedback;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ApiRouterTest {

    @Mock private DynamoDbRepository repository;
    private ApiRouter router;

    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        router = new ApiRouter(repository);
    }

    @Test
    void getDigestReturnsDigestForDate() throws Exception {
        Digest digest = new Digest("2026-06-12",
            List.of(new Digest.DigestEntry("id1", "Title", "Summary", "Anthropic", "https://url.com", 9)),
            List.of(),
            "2026-06-12T06:30:00Z");
        when(repository.getDigest("2026-06-12")).thenReturn(Optional.of(digest));

        ApiRouter.Response response = router.handleGet("/digest", Map.of("date", "2026-06-12"));

        assertEquals(200, response.statusCode());
        assertTrue(response.body().contains("Title"));
    }

    @Test
    void getDigestReturns404WhenMissing() {
        when(repository.getDigest("2026-06-12")).thenReturn(Optional.empty());

        ApiRouter.Response response = router.handleGet("/digest", Map.of("date", "2026-06-12"));

        assertEquals(404, response.statusCode());
    }

    @Test
    void postFeedbackSavesAndReturns200() {
        String body = """
            {"article_id": "id1", "digest_date": "2026-06-12", "feedback": "like"}
            """;

        ApiRouter.Response response = router.handlePost("/feedback", body);

        assertEquals(200, response.statusCode());
        verify(repository).saveFeedback(any(Feedback.class));
    }
}
