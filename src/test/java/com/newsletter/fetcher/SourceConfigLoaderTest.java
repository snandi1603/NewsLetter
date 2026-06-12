package com.newsletter.fetcher;

import com.newsletter.model.SourceConfig;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceConfigLoaderTest {

    @Test
    void loadsConfigFromYamlString() throws Exception {
        String yaml = """
            rss:
              - name: Anthropic
                url: https://anthropic.com/blog/rss
                type: company_blog
              - name: OpenAI
                url: https://openai.com/blog/rss
                type: company_blog
            twitter:
              - handle: karpathy
              - handle: ylecun
            substack:
              - name: AI News
                url: https://ainews.substack.com/feed
            medium:
              - name: Towards AI
                url: https://pub.towardsai.net/feed
            """;

        SourceConfigLoader loader = new SourceConfigLoader();
        SourceConfig config = loader.parseYaml(yaml);

        assertEquals(2, config.rss().size());
        assertEquals("OpenAI", config.rss().get(1).name());
        assertEquals(2, config.twitter().size());
        assertEquals("ylecun", config.twitter().get(1).handle());
    }
}
