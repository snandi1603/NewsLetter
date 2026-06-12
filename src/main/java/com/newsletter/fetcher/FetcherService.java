package com.newsletter.fetcher;

import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Article;
import com.newsletter.model.SourceConfig;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class FetcherService {

    private final DynamoDbRepository repository;
    private final RssFetcher rssFetcher;
    private final TwitterFetcher twitterFetcher;

    public FetcherService(DynamoDbRepository repository, RssFetcher rssFetcher, TwitterFetcher twitterFetcher) {
        this.repository = repository;
        this.rssFetcher = rssFetcher;
        this.twitterFetcher = twitterFetcher;
    }

    public int fetchAll(SourceConfig config) {
        List<Article> allArticles = new ArrayList<>();

        ExecutorService executor = Executors.newFixedThreadPool(4);
        List<Future<List<Article>>> futures = new ArrayList<>();

        for (var rss : config.rss()) {
            futures.add(executor.submit(() -> rssFetcher.fetchFromUrl(rss.url(), rss.name(), "rss")));
        }

        for (var sub : config.substack()) {
            futures.add(executor.submit(() -> rssFetcher.fetchFromUrl(sub.url(), sub.name(), "substack")));
        }

        for (var med : config.medium()) {
            futures.add(executor.submit(() -> rssFetcher.fetchFromUrl(med.url(), med.name(), "medium")));
        }

        for (var tw : config.twitter()) {
            futures.add(executor.submit(() -> twitterFetcher.fetchTweets(tw.handle())));
        }

        for (Future<List<Article>> future : futures) {
            try {
                allArticles.addAll(future.get(30, TimeUnit.SECONDS));
            } catch (Exception e) {
                System.err.println("Source fetch failed: " + e.getMessage());
            }
        }
        executor.shutdown();

        int savedCount = 0;
        for (Article article : allArticles) {
            if (!repository.exists(article.articleId(), article.fetchDate())) {
                repository.saveArticle(article);
                savedCount++;
            }
        }
        return savedCount;
    }
}
