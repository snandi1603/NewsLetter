package com.newsletter.fetcher;

import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.newsletter.model.SourceConfig;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.GetObjectRequest;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

public class SourceConfigLoader {

    private final YAMLMapper yamlMapper = new YAMLMapper();

    public SourceConfig parseYaml(String yaml) throws Exception {
        return yamlMapper.readValue(yaml, SourceConfig.class);
    }

    public SourceConfig loadFromS3(S3Client s3, String bucket, String key) throws Exception {
        GetObjectRequest request = GetObjectRequest.builder()
            .bucket(bucket)
            .key(key)
            .build();
        try (InputStream is = s3.getObject(request)) {
            String yaml = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            return parseYaml(yaml);
        }
    }
}
