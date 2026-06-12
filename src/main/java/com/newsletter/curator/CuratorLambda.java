package com.newsletter.curator;

import com.amazonaws.services.lambda.runtime.Context;
import com.amazonaws.services.lambda.runtime.RequestHandler;
import com.newsletter.common.AwsClientFactory;
import com.newsletter.common.DynamoDbRepository;
import com.newsletter.model.Digest;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;
import java.time.LocalDate;
import java.util.Map;

public class CuratorLambda implements RequestHandler<Map<String, Object>, Map<String, Object>> {

    private final CuratorService curatorService;
    private final HtmlGenerator htmlGenerator;
    private final S3Client s3;
    private final String bucketName;

    public CuratorLambda() {
        var dynamo = AwsClientFactory.dynamoDb();
        this.s3 = AwsClientFactory.s3();
        this.bucketName = System.getenv("BUCKET_NAME");
        String apiUrl = System.getenv("API_URL");
        String anthropicKey = System.getenv("ANTHROPIC_API_KEY");

        DynamoDbRepository repository = new DynamoDbRepository(dynamo,
            System.getenv("ARTICLES_TABLE"),
            System.getenv("DIGESTS_TABLE"),
            System.getenv("FEEDBACK_TABLE"));
        ClaudeClient claudeClient = new ClaudeClient(anthropicKey);
        this.htmlGenerator = new HtmlGenerator(apiUrl);
        this.curatorService = new CuratorService(repository, claudeClient, htmlGenerator);
    }

    @Override
    public Map<String, Object> handleRequest(Map<String, Object> event, Context context) {
        try {
            String today = LocalDate.now().toString();
            Digest digest = curatorService.curate(today);

            String html = curatorService.generateHtml(digest);
            uploadToS3("digest/" + today + ".html", html, "text/html");

            String indexHtml = htmlGenerator.generateIndexRedirect(today);
            uploadToS3("index.html", indexHtml, "text/html");

            context.getLogger().log("Curator generated digest for " + today);
            return Map.of("statusCode", 200, "date", today);
        } catch (Exception e) {
            context.getLogger().log("Curator failed: " + e.getMessage());
            return Map.of("statusCode", 500, "error", e.getMessage());
        }
    }

    private void uploadToS3(String key, String content, String contentType) {
        s3.putObject(
            PutObjectRequest.builder().bucket(bucketName).key(key).contentType(contentType).build(),
            RequestBody.fromString(content)
        );
    }
}
