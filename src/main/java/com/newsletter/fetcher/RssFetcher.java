package com.newsletter.fetcher;

import com.newsletter.model.Article;
import com.rometools.rome.feed.synd.SyndEntry;
import com.rometools.rome.feed.synd.SyndFeed;
import com.rometools.rome.io.SyndFeedInput;
import com.rometools.rome.io.XmlReader;
import java.io.InputStream;
import java.net.URI;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class RssFetcher {

    public List<Article> fetchFromUrl(String url, String sourceName, String sourceType) {
        try (InputStream stream = URI.create(url).toURL().openStream()) {
            return parseFromStream(stream, sourceName, sourceType);
        } catch (Exception e) {
            System.err.println("Failed to fetch RSS from " + url + ": " + e.getMessage());
            return List.of();
        }
    }

    public List<Article> parseFromStream(InputStream stream, String sourceName, String sourceType) throws Exception {
        SyndFeedInput input = new SyndFeedInput();
        SyndFeed feed = input.build(new XmlReader(stream));

        List<Article> articles = new ArrayList<>();
        String today = LocalDate.now().toString();

        for (SyndEntry entry : feed.getEntries()) {
            String url = entry.getLink();
            String title = entry.getTitle();
            String content = entry.getDescription() != null ? entry.getDescription().getValue() : "";
            String publishedAt = entry.getPublishedDate() != null
                ? entry.getPublishedDate().toInstant().toString()
                : Instant.now().toString();

            articles.add(new Article(
                Article.generateId(url),
                today,
                sourceName,
                sourceType,
                url,
                title,
                content,
                null,
                0,
                List.of(),
                publishedAt,
                Instant.now().toString()
            ));
        }
        return articles;
    }
}
