package com.newsletter.cdk;

import software.amazon.awscdk.*;
import software.amazon.awscdk.services.dynamodb.*;
import software.amazon.awscdk.services.events.*;
import software.amazon.awscdk.services.events.targets.*;
import software.amazon.awscdk.services.lambda.Runtime;
import software.amazon.awscdk.services.s3.*;
import software.amazon.awscdk.services.cloudfront.*;
import software.amazon.awscdk.services.cloudfront.origins.*;
import software.amazon.awscdk.services.apigateway.*;
import software.constructs.Construct;
import java.util.Map;

public class NewsletterStack extends Stack {

    public NewsletterStack(final Construct scope, final String id) {
        super(scope, id);

        // DynamoDB Tables
        Table articlesTable = Table.Builder.create(this, "ArticlesTable")
            .tableName("newsletter-articles")
            .partitionKey(Attribute.builder().name("article_id").type(AttributeType.STRING).build())
            .sortKey(Attribute.builder().name("fetch_date").type(AttributeType.STRING).build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .removalPolicy(RemovalPolicy.RETAIN)
            .build();

        articlesTable.addGlobalSecondaryIndex(GlobalSecondaryIndexProps.builder()
            .indexName("fetch_date-index")
            .partitionKey(Attribute.builder().name("fetch_date").type(AttributeType.STRING).build())
            .build());

        Table digestsTable = Table.Builder.create(this, "DigestsTable")
            .tableName("newsletter-digests")
            .partitionKey(Attribute.builder().name("digest_date").type(AttributeType.STRING).build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .removalPolicy(RemovalPolicy.RETAIN)
            .build();

        Table feedbackTable = Table.Builder.create(this, "FeedbackTable")
            .tableName("newsletter-feedback")
            .partitionKey(Attribute.builder().name("article_id").type(AttributeType.STRING).build())
            .sortKey(Attribute.builder().name("digest_date").type(AttributeType.STRING).build())
            .billingMode(BillingMode.PAY_PER_REQUEST)
            .removalPolicy(RemovalPolicy.RETAIN)
            .build();

        // S3 Bucket for static site
        Bucket siteBucket = Bucket.Builder.create(this, "SiteBucket")
            .bucketName("ai-coffee-newsletter-site")
            .websiteIndexDocument("index.html")
            .publicReadAccess(true)
            .blockPublicAccess(BlockPublicAccess.Builder.create()
                .blockPublicAcls(false)
                .ignorePublicAcls(false)
                .blockPublicPolicy(false)
                .restrictPublicBuckets(false)
                .build())
            .removalPolicy(RemovalPolicy.RETAIN)
            .build();

        // CloudFront Distribution
        Distribution distribution = Distribution.Builder.create(this, "Distribution")
            .defaultBehavior(BehaviorOptions.builder()
                .origin(new S3Origin(siteBucket))
                .viewerProtocolPolicy(ViewerProtocolPolicy.REDIRECT_TO_HTTPS)
                .build())
            .build();

        // Lambda environment
        Map<String, String> lambdaEnv = Map.of(
            "ARTICLES_TABLE", articlesTable.getTableName(),
            "DIGESTS_TABLE", digestsTable.getTableName(),
            "FEEDBACK_TABLE", feedbackTable.getTableName(),
            "BUCKET_NAME", siteBucket.getBucketName()
        );

        // Fetcher Lambda
        software.amazon.awscdk.services.lambda.Function fetcherLambda =
            software.amazon.awscdk.services.lambda.Function.Builder.create(this, "FetcherLambda")
            .functionName("newsletter-fetcher")
            .runtime(Runtime.JAVA_21)
            .handler("com.newsletter.fetcher.FetcherLambda::handleRequest")
            .code(software.amazon.awscdk.services.lambda.Code.fromAsset("../build/libs/ai-coffee-newsletter-1.0.0-all.jar"))
            .memorySize(512)
            .timeout(Duration.minutes(5))
            .environment(lambdaEnv)
            .build();

        // Curator Lambda (needs extra env vars)
        software.amazon.awscdk.services.lambda.Function curatorLambda =
            software.amazon.awscdk.services.lambda.Function.Builder.create(this, "CuratorLambda")
            .functionName("newsletter-curator")
            .runtime(Runtime.JAVA_21)
            .handler("com.newsletter.curator.CuratorLambda::handleRequest")
            .code(software.amazon.awscdk.services.lambda.Code.fromAsset("../build/libs/ai-coffee-newsletter-1.0.0-all.jar"))
            .memorySize(512)
            .timeout(Duration.minutes(5))
            .environment(lambdaEnv)
            .build();

        // API Lambda
        software.amazon.awscdk.services.lambda.Function apiLambda =
            software.amazon.awscdk.services.lambda.Function.Builder.create(this, "ApiLambda")
            .functionName("newsletter-api")
            .runtime(Runtime.JAVA_21)
            .handler("com.newsletter.api.ApiLambda::handleRequest")
            .code(software.amazon.awscdk.services.lambda.Code.fromAsset("../build/libs/ai-coffee-newsletter-1.0.0-all.jar"))
            .memorySize(256)
            .timeout(Duration.seconds(30))
            .environment(lambdaEnv)
            .build();

        // Grant permissions
        articlesTable.grantReadWriteData(fetcherLambda);
        articlesTable.grantReadData(curatorLambda);
        articlesTable.grantReadData(apiLambda);
        digestsTable.grantReadWriteData(curatorLambda);
        digestsTable.grantReadData(apiLambda);
        feedbackTable.grantReadWriteData(apiLambda);
        siteBucket.grantReadWrite(fetcherLambda);
        siteBucket.grantReadWrite(curatorLambda);

        // API Gateway
        RestApi api = RestApi.Builder.create(this, "NewsletterApi")
            .restApiName("newsletter-api")
            .defaultCorsPreflightOptions(CorsOptions.builder()
                .allowOrigins(Cors.ALL_ORIGINS)
                .allowMethods(Cors.ALL_METHODS)
                .build())
            .build();

        LambdaIntegration apiIntegration = new LambdaIntegration(apiLambda);
        api.getRoot().addResource("digest").addMethod("GET", apiIntegration);
        api.getRoot().addResource("feedback").addMethod("POST", apiIntegration);
        api.getRoot().addResource("sources").addMethod("GET", apiIntegration);

        // EventBridge schedules
        Rule fetcherRule = Rule.Builder.create(this, "FetcherSchedule")
            .schedule(Schedule.cron(CronOptions.builder().hour("6").minute("0").build()))
            .build();
        fetcherRule.addTarget(new LambdaFunction(fetcherLambda));

        Rule curatorRule = Rule.Builder.create(this, "CuratorSchedule")
            .schedule(Schedule.cron(CronOptions.builder().hour("6").minute("15").build()))
            .build();
        curatorRule.addTarget(new LambdaFunction(curatorLambda));

        // Outputs
        new CfnOutput(this, "SiteUrl", CfnOutputProps.builder()
            .value(distribution.getDistributionDomainName()).build());
        new CfnOutput(this, "ApiUrl", CfnOutputProps.builder()
            .value(api.getUrl()).build());
    }
}
