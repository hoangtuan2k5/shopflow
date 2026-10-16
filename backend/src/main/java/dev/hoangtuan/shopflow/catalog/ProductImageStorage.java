package dev.hoangtuan.shopflow.catalog;

import java.net.URI;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.S3Configuration;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Configuration(proxyBeanMethods = false)
class R2Configuration {
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

@Component
class ProductImageStorage {
  private static final Logger log = LoggerFactory.getLogger(ProductImageStorage.class);
  private final S3Client client;
  private final String bucket;
  private final String publicBaseUrl;

  ProductImageStorage(
      S3Client client,
      @Value("${shopflow.r2.bucket}") String bucket,
      @Value("${shopflow.r2.public-base-url}") String publicBaseUrl) {
    this.client = client;
    this.bucket = bucket;
    this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
  }

  String url(String key) {
    return key == null ? null : publicBaseUrl + "/" + key;
  }

  void put(String key, String contentType, byte[] bytes) {
    try {
      client.putObject(
          PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
          RequestBody.fromBytes(bytes));
    } catch (RuntimeException e) {
      throw new ProductImageException(503, "Image storage unavailable");
    }
  }

  void delete(String key) {
    if (key == null) return;
    try {
      client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    } catch (RuntimeException e) {
      log.warn("Unable to delete product image {} ({})", key, e.getClass().getSimpleName());
    }
  }
}
