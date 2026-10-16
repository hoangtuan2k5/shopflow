package dev.hoangtuan.shopflow.catalog;

import static org.mockito.Mockito.mock;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;
import software.amazon.awssdk.services.s3.S3Client;

@Configuration(proxyBeanMethods = false)
@Profile("test")
class R2TestConfiguration {
  @Bean
  S3Client productImageClient() {
    return mock(S3Client.class);
  }
}
