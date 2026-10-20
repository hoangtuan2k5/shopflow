package dev.hoangtuan.shopflow.config;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class SearchConfiguration {
  @Bean
  RestClient.Builder catalogIndexRestClientBuilder() {
    return builder(Duration.ofSeconds(60));
  }

  public static RestClient.Builder builder(Duration timeout) {
    var http =
        HttpClient.newBuilder()
            .connectTimeout(
                timeout.compareTo(Duration.ofSeconds(10)) < 0 ? timeout : Duration.ofSeconds(10))
            .build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(timeout);
    return RestClient.builder().requestFactory(factory);
  }
}
