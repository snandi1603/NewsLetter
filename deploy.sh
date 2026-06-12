#!/bin/bash
set -e

echo "=== AI Coffee Newsletter Deployment ==="

echo "Building fat JAR..."
./gradlew shadowJar

echo "Deploying CDK stack..."
cd cdk
cdk deploy --require-approval never --profile dynamodblearning-java
cd ..

echo "Uploading static assets..."
aws s3 sync frontend/ s3://ai-coffee-newsletter-site/assets/ \
    --exclude "template.html" \
    --cache-control "max-age=86400" \
    --profile dynamodblearning-java

echo "Uploading sources config..."
aws s3 cp sources.yaml s3://ai-coffee-newsletter-site/sources.yaml \
    --profile dynamodblearning-java

echo "=== Deployment Complete ==="
echo "Visit your CloudFront URL to see the newsletter."
