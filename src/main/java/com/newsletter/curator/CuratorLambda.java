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
import java.util.List;
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
            LocalDate todayDate = LocalDate.now();
            String today = todayDate.toString();
            String yesterday = todayDate.minusDays(1).toString();

            Digest digest = curatorService.curate(today);

            // Regenerate all existing pages with correct navigation links
            regenerateAllPages(todayDate, context);

            String indexHtml = htmlGenerator.generateIndexRedirect(today);
            uploadToS3("index.html", indexHtml, "text/html");

            context.getLogger().log("Curator generated digest for " + today);
            return Map.of("statusCode", 200, "date", today);
        } catch (Exception e) {
            context.getLogger().log("Curator failed: " + e.getMessage());
            return Map.of("statusCode", 500, "error", e.getMessage());
        }
    }

    private void regenerateAllPages(LocalDate todayDate, Context context) {
        List<String> dates = new java.util.ArrayList<>();
        // Look back up to 30 days for existing digests
        for (int i = 30; i >= 0; i--) {
            String date = todayDate.minusDays(i).toString();
            Digest d = curatorService.getDigest(date);
            if (d != null && (!d.top5().isEmpty() || !d.more().isEmpty())) {
                dates.add(date);
            }
        }

        for (int i = 0; i < dates.size(); i++) {
            String date = dates.get(i);
            String prev = i > 0 ? dates.get(i - 1) : null;
            String next = i < dates.size() - 1 ? dates.get(i + 1) : null;
            Digest d = curatorService.getDigest(date);
            String html = curatorService.generateHtml(d, prev, next);
            uploadToS3("digest/" + date + ".html", html, "text/html");
        }
        context.getLogger().log("Regenerated " + dates.size() + " digest pages");
    }

    private void uploadToS3(String key, String content, String contentType) {
        String cacheControl = "max-age=60";
        s3.putObject(
            PutObjectRequest.builder()
                .bucket(bucketName)
                .key(key)
                .contentType(contentType)
                .cacheControl(cacheControl)
                .build(),
            RequestBody.fromString(content)
        );
    }
}
