package com.newsletter.curator;

import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Article;
import com.newsletter.model.Digest;
import java.time.Instant;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public class CuratorService {

    private static final int TOP_N = 5;
    private static final int MAX_ARTICLES = 50;

    private final DynamoDbRepository repository;
    private final ClaudeClient claudeClient;
    private final HtmlGenerator htmlGenerator;

    public CuratorService(DynamoDbRepository repository, ClaudeClient claudeClient, HtmlGenerator htmlGenerator) {
        this.repository = repository;
        this.claudeClient = claudeClient;
        this.htmlGenerator = htmlGenerator;
    }

    public Digest curate(String date) throws Exception {
        List<Article> articles = repository.getArticlesByDate(date);

        if (articles.isEmpty()) {
            Digest empty = new Digest(date, List.of(), List.of(), Instant.now().toString());
            repository.saveDigest(empty);
            return empty;
        }

        if (articles.size() > MAX_ARTICLES) {
            articles = articles.subList(0, MAX_ARTICLES);
        }

        List<ClaudeClient.CurationResult> results = new ArrayList<>(claudeClient.curateArticles(articles));
        results.sort((a, b) -> Integer.compare(b.score(), a.score()));

        Map<String, Article> articleMap = articles.stream()
            .collect(Collectors.toMap(Article::articleId, a -> a));

        List<Digest.DigestEntry> top5 = new ArrayList<>();
        List<Digest.DigestEntry> more = new ArrayList<>();

        for (int i = 0; i < results.size(); i++) {
            ClaudeClient.CurationResult r = results.get(i);
            Article a = articleMap.get(r.articleId());
            if (a == null) continue;

            Digest.DigestEntry entry = new Digest.DigestEntry(
                r.articleId(), a.title(), r.summary(), a.sourceName(), a.originalUrl(), r.score()
            );

            if (i < TOP_N) {
                top5.add(entry);
            } else {
                more.add(entry);
            }
        }

        Digest digest = new Digest(date, top5, more, Instant.now().toString());
        repository.saveDigest(digest);
        return digest;
    }

    public String generateHtml(Digest digest) {
        String prevDate = LocalDate.parse(digest.digestDate()).minusDays(1).toString();
        return htmlGenerator.generateDigestPage(digest, prevDate, null);
    }
}
