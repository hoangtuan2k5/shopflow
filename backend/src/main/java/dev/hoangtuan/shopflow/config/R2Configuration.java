package dev.hoangtuan.shopflow.config;

import java.net.URI;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;

@Configuration(proxyBeanMethods = false)
public class R2Configuration {
  @Bean
  @Profile("!test")
  S3Client productImageClient(
      @Value("${shopflow.r2.endpoint}") String endpoint,
      @Value("${shopflow.r2.access-key-id}") String accessKey,
      @Value("${shopflow.r2.secret-access-key}") String secret,
      @Value("${shopflow.r2.bucket}") String bucket,
      @Value("${shopflow.r2.public-base-url}") String publicBaseUrl,
      org.springframework.core.env.Environment environment) {
    if (environment.matchesProfiles("staging")
        && (endpoint.isBlank()
            || accessKey.isBlank()
            || secret.isBlank()
            || bucket.isBlank()
            || publicBaseUrl.isBlank())) {
      throw new IllegalStateException("All five R2 settings are required in staging");
    }
    return S3Client.builder()
        .endpointOverride(URI.create(endpoint))
        .region(Region.of("auto"))
        .credentialsProvider(
            accessKey.isBlank() || secret.isBlank()
                ? AnonymousCredentialsProvider.create()
                : StaticCredentialsProvider.create(AwsBasicCredentials.create(accessKey, secret)))
        .serviceConfiguration(
            S3Configuration.builder()
                .pathStyleAccessEnabled(true)
                .chunkedEncodingEnabled(false)
                .build())
        .overrideConfiguration(c -> c.apiCallTimeout(Duration.ofSeconds(30)))
        .build();
  }
}
