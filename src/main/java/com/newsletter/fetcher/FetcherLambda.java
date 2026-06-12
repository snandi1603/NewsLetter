package com.newsletter.fetcher;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.newsletter.common.AwsClientFactory;
import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.SourceConfig;
import software.amazon.awssdk.services.s3.S3Client;
import java.util.Map;

public class FetcherLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final DynamoDbRepository repository;
    private final SourceConfigLoader configLoader;
    private final FetcherService fetcherService;
    private final S3Client s3;
    private final String bucketName;

    public FetcherLambda() {
        var dynamo = AwsClientFactory.dynamoDb();
        this.s3 = AwsClientFactory.s3();
        this.bucketName = System.getenv("BUCKET_NAME");
        this.repository = new DynamoDbRepository(dynamo,
            System.getenv("ARTICLES_TABLE"),
            System.getenv("DIGESTS_TABLE"),
            System.getenv("FEEDBACK_TABLE"));
        this.configLoader = new SourceConfigLoader();
        this.fetcherService = new FetcherService(repository, new RssFetcher(), new TwitterFetcher());
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            SourceConfig config = configLoader.loadFromS3(s3, bucketName, "sources.yaml");
            int saved = fetcherService.fetchAll(config);
            context.getLogger().log("Fetched and saved " + saved + " new articles");
            return Map.of("statusCode", 200, "saved", saved);
        } catch (Exception e) {
            context.getLogger().log("Fetcher failed: " + e.getMessage());
            return Map.of("statusCode", 500, "error", e.getMessage());
        }
    }
}
