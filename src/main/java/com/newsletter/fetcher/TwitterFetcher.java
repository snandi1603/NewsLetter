package com.newsletter.fetcher;

import com.newsletter.model.Article;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.select.Elements;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class TwitterFetcher {

    private static final String NITTER_BASE = "https://nitter.net";

    public List<Article> fetchTweets(String handle) {
        try {
            String url = NITTER_BASE + "/" + handle;
            Document doc = Jsoup.connect(url)
                .userAgent("Mozilla/5.0")
                .timeout(10000)
                .get();
            return parseNitterHtml(doc.html(), handle);
        } catch (Exception e) {
            System.err.println("Failed to fetch tweets for @" + handle + ": " + e.getMessage());
            return List.of();
        }
    }

    public List<Article> parseHtml(String html, String handle) {
        Document doc = Jsoup.parse(html);
        Elements tweets = doc.select("article[data-testid=tweet]");

        if (tweets.isEmpty()) {
            return parseNitterHtml(html, handle);
        }

        List<Article> articles = new ArrayList<>();
        String today = LocalDate.now().toString();

        for (Element tweet : tweets) {
            Element textEl = tweet.selectFirst("[data-testid=tweetText]");
            Element timeEl = tweet.selectFirst("time");
            Element linkEl = tweet.selectFirst("a[href*=status]");

            if (textEl == null) continue;

            String content = textEl.text();
            String tweetUrl = linkEl != null
                ? "https://x.com" + linkEl.attr("href")
                : "https://x.com/" + handle;
            String publishedAt = timeEl != null
                ? timeEl.attr("datetime")
                : Instant.now().toString();

            articles.add(new Article(
                Article.generateId(tweetUrl),
                today,
                "@" + handle,
                "twitter",
                tweetUrl,
                content.length() > 80 ? content.substring(0, 80) + "..." : content,
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

    private List<Article> parseNitterHtml(String html, String handle) {
        Document doc = Jsoup.parse(html);
        Elements tweets = doc.select(".timeline-item .tweet-content");

        List<Article> articles = new ArrayList<>();
        String today = LocalDate.now().toString();

        for (Element tweet : tweets) {
            String content = tweet.text();
            if (content.isBlank()) continue;

            String tweetUrl = "https://x.com/" + handle + "/status/" + System.nanoTime();

            articles.add(new Article(
                Article.generateId(content),
                today,
                "@" + handle,
                "twitter",
                tweetUrl,
                content.length() > 80 ? content.substring(0, 80) + "..." : content,
                content,
                null,
                0,
                List.of(),
                publishedAt(doc, tweet),
                Instant.now().toString()
            ));
        }
        return articles;
    }

    private String publishedAt(Document doc, Element tweet) {
        Element time = tweet.parent() != null ? tweet.parent().selectFirst(".tweet-date a") : null;
        return time != null ? time.attr("title") : Instant.now().toString();
    }
}
