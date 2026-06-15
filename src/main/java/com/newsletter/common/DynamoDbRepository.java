package com.newsletter.common;

import com.newsletter.model.Article;
import com.newsletter.model.Digest;
import com.newsletter.model.Feedback;
import com.fasterxml.jackson.databind.ObjectMapper;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.dynamodb.model.*;
import java.util.*;
import java.util.stream.Collectors;

public class DynamoDbRepository {

    private final DynamoDbClient dynamo;
    private final String articlesTable;
    private final String digestsTable;
    private final String feedbackTable;
    private final ObjectMapper mapper = new ObjectMapper();

    public DynamoDbRepository(DynamoDbClient dynamo, String articlesTable, String digestsTable, String feedbackTable) {
        this.dynamo = dynamo;
        this.articlesTable = articlesTable;
        this.digestsTable = digestsTable;
        this.feedbackTable = feedbackTable;
    }

    public boolean exists(String articleId) {
        QueryResponse response = dynamo.query(QueryRequest.builder()
            .tableName(articlesTable)
            .keyConditionExpression("article_id = :id")
            .expressionAttributeValues(Map.of(
                ":id", AttributeValue.builder().s(articleId).build()
            ))
            .limit(1)
            .build());
        return !response.items().isEmpty();
    }

    public void saveArticle(Article article) {
        dynamo.putItem(PutItemRequest.builder()
            .tableName(articlesTable)
            .item(articleToItem(article))
            .build());
    }

    public void saveDigest(Digest digest) {
        try {
            Map<String, AttributeValue> item = new HashMap<>();
            item.put("digest_date", AttributeValue.builder().s(digest.digestDate()).build());
            item.put("top_5", AttributeValue.builder().s(mapper.writeValueAsString(digest.top5())).build());
            item.put("more", AttributeValue.builder().s(mapper.writeValueAsString(digest.more())).build());
            item.put("generated_at", AttributeValue.builder().s(digest.generatedAt()).build());
            dynamo.putItem(PutItemRequest.builder().tableName(digestsTable).item(item).build());
        } catch (Exception e) {
            throw new RuntimeException("Failed to save digest", e);
        }
    }

    public void saveFeedback(Feedback feedback) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("article_id", AttributeValue.builder().s(feedback.articleId()).build());
        item.put("digest_date", AttributeValue.builder().s(feedback.digestDate()).build());
        item.put("feedback", AttributeValue.builder().s(feedback.feedback()).build());
        item.put("source_name", AttributeValue.builder().s(feedback.sourceName()).build());
        if (feedback.tags() != null && !feedback.tags().isEmpty()) {
            item.put("tags", AttributeValue.builder().ss(feedback.tags()).build());
        }
        item.put("created_at", AttributeValue.builder().s(feedback.createdAt()).build());
        dynamo.putItem(PutItemRequest.builder().tableName(feedbackTable).item(item).build());
    }

    public List<Article> getArticlesByDate(String fetchDate) {
        QueryResponse response = dynamo.query(QueryRequest.builder()
            .tableName(articlesTable)
            .indexName("fetch_date-index")
            .keyConditionExpression("fetch_date = :date")
            .expressionAttributeValues(Map.of(
                ":date", AttributeValue.builder().s(fetchDate).build()
            ))
            .build());
        return response.items().stream().map(this::itemToArticle).collect(Collectors.toList());
    }

    public Optional<Digest> getDigest(String date) {
        GetItemResponse response = dynamo.getItem(GetItemRequest.builder()
            .tableName(digestsTable)
            .key(Map.of("digest_date", AttributeValue.builder().s(date).build()))
            .build());
        if (!response.hasItem() || response.item().isEmpty()) return Optional.empty();
        return Optional.of(itemToDigest(response.item()));
    }

    public Map<String, AttributeValue> articleToItem(Article article) {
        Map<String, AttributeValue> item = new HashMap<>();
        item.put("article_id", AttributeValue.builder().s(article.articleId()).build());
        item.put("fetch_date", AttributeValue.builder().s(article.fetchDate()).build());
        item.put("source_name", AttributeValue.builder().s(article.sourceName()).build());
        item.put("source_type", AttributeValue.builder().s(article.sourceType()).build());
        item.put("original_url", AttributeValue.builder().s(article.originalUrl()).build());
        item.put("title", AttributeValue.builder().s(article.title()).build());
        item.put("raw_content", AttributeValue.builder().s(article.rawContent() != null ? article.rawContent() : "").build());
        if (article.summary() != null) item.put("summary", AttributeValue.builder().s(article.summary()).build());
        item.put("relevance_score", AttributeValue.builder().n(String.valueOf(article.relevanceScore())).build());
        if (article.tags() != null && !article.tags().isEmpty()) item.put("tags", AttributeValue.builder().ss(article.tags()).build());
        item.put("published_at", AttributeValue.builder().s(article.publishedAt()).build());
        item.put("created_at", AttributeValue.builder().s(article.createdAt()).build());
        return item;
    }

    private Article itemToArticle(Map<String, AttributeValue> item) {
        return new Article(
            item.get("article_id").s(),
            item.get("fetch_date").s(),
            item.get("source_name").s(),
            item.get("source_type").s(),
            item.get("original_url").s(),
            item.get("title").s(),
            item.containsKey("raw_content") ? item.get("raw_content").s() : "",
            item.containsKey("summary") ? item.get("summary").s() : null,
            item.containsKey("relevance_score") ? Integer.parseInt(item.get("relevance_score").n()) : 0,
            item.containsKey("tags") ? item.get("tags").ss() : List.of(),
            item.get("published_at").s(),
            item.get("created_at").s()
        );
    }

    private Digest itemToDigest(Map<String, AttributeValue> item) {
        try {
            String digestDate = item.get("digest_date").s();
            List<Digest.DigestEntry> top5 = mapper.readValue(item.get("top_5").s(),
                mapper.getTypeFactory().constructCollectionType(List.class, Digest.DigestEntry.class));
            List<Digest.DigestEntry> more = mapper.readValue(item.get("more").s(),
                mapper.getTypeFactory().constructCollectionType(List.class, Digest.DigestEntry.class));
            String generatedAt = item.get("generated_at").s();
            return new Digest(digestDate, top5, more, generatedAt);
        } catch (Exception e) {
            throw new RuntimeException("Failed to parse digest", e);
        }
    }
}
