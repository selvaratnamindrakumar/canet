package com.canet.migration.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.enhanced.dynamodb.DynamoDbEnhancedClient;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.dynamodb.DynamoDbClient;

import java.net.URI;

@Configuration
public class DynamoDbConfig {

    @Value("${aws.region:us-east-1}")
    private String awsRegion;

    /**
     * Optional: override endpoint for DynamoDB Local (dev/test).
     * Set aws.dynamodb.endpoint=http://localhost:8000 in application-local.properties.
     */
    @Value("${aws.dynamodb.endpoint:}")
    private String dynamoEndpoint;

    @Value("${aws.dynamodb.local:false}")
    private boolean isLocal;

    @Bean
    public DynamoDbClient dynamoDbClient() {
        var builder = DynamoDbClient.builder()
                .region(Region.of(awsRegion))
                .httpClient(UrlConnectionHttpClient.create());

        if (isLocal && !dynamoEndpoint.isBlank()) {
            // DynamoDB Local uses dummy credentials
            builder.endpointOverride(URI.create(dynamoEndpoint))
                   .credentialsProvider(StaticCredentialsProvider.create(
                           AwsBasicCredentials.create("local", "local")));
        } else {
            // Use the standard AWS credential chain (env vars, ~/.aws/credentials, IAM role)
            builder.credentialsProvider(DefaultCredentialsProvider.create());
        }

        return builder.build();
    }

    @Bean
    public DynamoDbEnhancedClient dynamoDbEnhancedClient(DynamoDbClient dynamoDbClient) {
        return DynamoDbEnhancedClient.builder()
                .dynamoDbClient(dynamoDbClient)
                .build();
    }
}
