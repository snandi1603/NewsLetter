package com.newsletter.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.util.List;

@JsonIgnoreProperties(ignoreUnknown = true)
public record SourceConfig(
    List<RssSource> rss,
    List<TwitterSource> twitter,
    List<FeedSource> substack,
    List<FeedSource> medium,
    List<ScrapeSource> scrape
) {
    public record RssSource(String name, String url, String type) {}
    public record TwitterSource(String handle) {}
    public record FeedSource(String name, String url) {}
    public record ScrapeSource(String name, String url, String linkPattern) {}
}
