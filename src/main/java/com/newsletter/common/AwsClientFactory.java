package com.newsletter.common;

import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;
import software.amazon.awssdk.services.s3.S3Client;

public class AwsClientFactory {
    private static final boolean IS_LAMBDA = System.getenv("AWS_LAMBDA_FUNCTION_NAME") != null;

    public static DynamoDbClient dynamoDb() {
        var builder = DynamoDbClient.builder().region(Region.US_EAST_1);
        if (!IS_LAMBDA) {
            builder.credentialsProvider(ProfileCredentialsProvider.create("dynamodblearning-java"));
        }
        return builder.build();
    }

    public static S3Client s3() {
        var builder = S3Client.builder().region(Region.US_EAST_1);
        if (!IS_LAMBDA) {
            builder.credentialsProvider(ProfileCredentialsProvider.create("dynamodblearning-java"));
        }
        return builder.build();
    }
}
