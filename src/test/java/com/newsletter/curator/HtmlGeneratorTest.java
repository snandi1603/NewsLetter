package com.newsletter.curator;

import com.newsletter.model.Digest;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class HtmlGeneratorTest {

    @Test
    void generatesHtmlWithTop5AndMore() {
        List<Digest.DigestEntry> top5 = List.of(
            new Digest.DigestEntry("id1", "Claude 4 Released", "Major new model.", "Anthropic", "https://anthropic.com/blog/claude4", 9),
            new Digest.DigestEntry("id2", "GPT-5 Announced", "OpenAI's next gen.", "OpenAI", "https://openai.com/blog/gpt5", 8)
        );
        List<Digest.DigestEntry> more = List.of(
            new Digest.DigestEntry("id3", "Scaling Laws", "New paper on scaling.", "@karpathy", "https://x.com/karpathy/1", 6)
        );
        Digest digest = new Digest("2026-06-12", top5, more, "2026-06-12T06:30:00Z");

        HtmlGenerator generator = new HtmlGenerator("https://api.example.com");
        String html = generator.generateDigestPage(digest, "2026-06-11", null);

        assertTrue(html.contains("Claude 4 Released"));
        assertTrue(html.contains("GPT-5 Announced"));
        assertTrue(html.contains("Scaling Laws"));
        assertTrue(html.contains("2026-06-12"));
        assertTrue(html.contains("2026-06-11"));
        assertTrue(html.contains("data-article-id=\"id1\""));
    }

    @Test
    void showsTomorrowLinkWhenProvided() {
        Digest digest = new Digest("2026-06-11", List.of(), List.of(), "2026-06-11T06:30:00Z");

        HtmlGenerator generator = new HtmlGenerator("https://api.example.com");
        String html = generator.generateDigestPage(digest, "2026-06-10", "2026-06-12");

        assertTrue(html.contains("2026-06-12"));
    }
}
