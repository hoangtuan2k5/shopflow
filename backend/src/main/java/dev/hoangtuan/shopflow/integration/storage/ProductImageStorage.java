package dev.hoangtuan.shopflow.integration.storage;

import dev.hoangtuan.shopflow.exception.catalog.ProductImageException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import software.amazon.awssdk.core.sync.RequestBody;
import software.amazon.awssdk.services.s3.S3Client;
import software.amazon.awssdk.services.s3.model.DeleteObjectRequest;
import software.amazon.awssdk.services.s3.model.PutObjectRequest;

@Component
public class ProductImageStorage {
  private static final Logger log = LoggerFactory.getLogger(ProductImageStorage.class);
  private final S3Client client;
  private final String bucket;
  private final String publicBaseUrl;

  public ProductImageStorage(
      S3Client client,
      @Value("${shopflow.r2.bucket}") String bucket,
      @Value("${shopflow.r2.public-base-url}") String publicBaseUrl) {
    this.client = client;
    this.bucket = bucket;
    this.publicBaseUrl = publicBaseUrl.replaceAll("/+$", "");
  }

  public String url(String key) {
    return key == null ? null : publicBaseUrl + "/" + key;
  }

  public void put(String key, String contentType, byte[] bytes) {
    try {
      client.putObject(
          PutObjectRequest.builder().bucket(bucket).key(key).contentType(contentType).build(),
          RequestBody.fromBytes(bytes));
    } catch (RuntimeException e) {
      throw new ProductImageException(503, "Image storage unavailable");
    }
  }

  public void delete(String key) {
    if (key == null) return;
    try {
      client.deleteObject(DeleteObjectRequest.builder().bucket(bucket).key(key).build());
    } catch (RuntimeException e) {
      log.warn("Unable to delete product image {} ({})", key, e.getClass().getSimpleName());
    }
  }
}
