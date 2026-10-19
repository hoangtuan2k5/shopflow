package dev.hoangtuan.shopflow.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import software.amazon.awssdk.services.s3.S3Client;

class R2ConfigurationTests {
  private final ApplicationContextRunner context =
      new ApplicationContextRunner()
          .withUserConfiguration(R2Configuration.class)
          .withPropertyValues(
              "shopflow.r2.endpoint=http://localhost:9",
              "shopflow.r2.bucket=dev-products",
              "shopflow.r2.public-base-url=https://media.example.invalid",
              "shopflow.r2.access-key-id=",
              "shopflow.r2.secret-access-key=");

  @Test
  void developmentStartsWithoutCredentialsOrNetworkCalls() {
    context
        .withInitializer(c -> c.getEnvironment().setActiveProfiles("dev"))
        .run(c -> assertThat(c).hasNotFailed().hasSingleBean(S3Client.class));
  }

  @Test
  void stagingRefusesBlankCredentialsAtStartup() {
    context
        .withInitializer(c -> c.getEnvironment().setActiveProfiles("staging"))
        .run(
            c ->
                assertThat(c)
                    .hasFailed()
                    .getFailure()
                    .hasRootCauseMessage("All five R2 settings are required in staging"));
  }
}
