package com.newsletter.fetcher;

import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Article;
import com.newsletter.model.SourceConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class FetcherService {

    private static final Logger log = LoggerFactory.getLogger(FetcherService.class);

    private final DynamoDbRepository repository;
    private final RssFetcher rssFetcher;
    private final TwitterFetcher twitterFetcher;
    private final WebScraperFetcher webScraperFetcher;

    public FetcherService(DynamoDbRepository repository, RssFetcher rssFetcher, TwitterFetcher twitterFetcher, WebScraperFetcher webScraperFetcher) {
        this.repository = repository;
        this.rssFetcher = rssFetcher;
        this.twitterFetcher = twitterFetcher;
        this.webScraperFetcher = webScraperFetcher;
    }

    public int fetchAll(SourceConfig config) {
        log.info("Starting fetch. Sources: {} RSS, {} Substack, {} Medium, {} Twitter, {} Scrape",
            config.rss().size(), config.substack().size(), config.medium().size(),
            config.twitter().size(), config.scrape() != null ? config.scrape().size() : 0);

        List<Article> allArticles = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<List<Article>>> futures = new ArrayList<>();

        for (var rss : config.rss()) {
            futures.add(executor.submit(() -> {
                log.info("Fetching RSS: {} ({})", rss.name(), rss.url());
                List<Article> articles = rssFetcher.fetchFromUrl(rss.url(), rss.name(), "rss");
                log.info("RSS {} returned {} articles", rss.name(), articles.size());
                return articles;
            }));
        }

        for (var sub : config.substack()) {
            futures.add(executor.submit(() -> {
                log.info("Fetching Substack: {} ({})", sub.name(), sub.url());
                List<Article> articles = rssFetcher.fetchFromUrl(sub.url(), sub.name(), "substack");
                log.info("Substack {} returned {} articles", sub.name(), articles.size());
                return articles;
            }));
        }

        for (var med : config.medium()) {
            futures.add(executor.submit(() -> {
                log.info("Fetching Medium: {} ({})", med.name(), med.url());
                List<Article> articles = rssFetcher.fetchFromUrl(med.url(), med.name(), "medium");
                log.info("Medium {} returned {} articles", med.name(), articles.size());
                return articles;
            }));
        }

        for (var tw : config.twitter()) {
            futures.add(executor.submit(() -> {
                log.info("Fetching Twitter: @{}", tw.handle());
                List<Article> articles = twitterFetcher.fetchTweets(tw.handle());
                log.info("Twitter @{} returned {} articles", tw.handle(), articles.size());
                return articles;
            }));
        }

        if (config.scrape() != null) {
            for (var scrape : config.scrape()) {
                futures.add(executor.submit(() -> {
                    log.info("Scraping: {} ({})", scrape.name(), scrape.url());
                    List<Article> articles = webScraperFetcher.fetchFromPage(scrape.url(), scrape.name(), scrape.linkPattern());
                    log.info("Scrape {} returned {} articles", scrape.name(), articles.size());
                    return articles;
                }));
            }
        }

        for (Future<List<Article>> future : futures) {
            try {
                allArticles.addAll(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                log.error("Source fetch failed: {}", e.getMessage());
            }
        }
        executor.shutdown();

        log.info("Total articles fetched: {}. Checking for duplicates...", allArticles.size());

        int savedCount = 0;
        int skippedCount = 0;
        for (Article article : allArticles) {
            if (!repository.exists(article.articleId())) {
                repository.saveArticle(article);
                savedCount++;
            } else {
                skippedCount++;
            }
        }

        log.info("Done. Saved: {}, Skipped (duplicates): {}", savedCount, skippedCount);
        return savedCount;
    }
}
