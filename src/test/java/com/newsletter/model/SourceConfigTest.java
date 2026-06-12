package com.newsletter.model;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SourceConfigTest {

    @Test
    void parsesYamlConfig() throws Exception {
        String yaml = """
            rss:
              - name: Anthropic
                url: https://anthropic.com/blog/rss
                type: company_blog
            twitter:
              - handle: karpathy
            substack:
              - name: AI News
                url: https://ainews.substack.com/feed
            medium:
              - name: Towards AI
                url: https://pub.towardsai.net/feed
            """;

        YAMLMapper mapper = new YAMLMapper();
        SourceConfig config = mapper.readValue(yaml, SourceConfig.class);

        assertEquals(1, config.rss().size());
        assertEquals("Anthropic", config.rss().get(0).name());
        assertEquals(1, config.twitter().size());
        assertEquals("karpathy", config.twitter().get(0).handle());
        assertEquals(1, config.substack().size());
        assertEquals(1, config.medium().size());
    }
}
