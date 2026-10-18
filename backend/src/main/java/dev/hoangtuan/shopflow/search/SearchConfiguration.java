package dev.hoangtuan.shopflow.search;

import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
class SearchConfiguration {
  @Bean
  RestClient.Builder catalogIndexRestClientBuilder() {
    var http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    var factory = new JdkClientHttpRequestFactory(http);
    factory.setReadTimeout(Duration.ofSeconds(60));
    return RestClient.builder().requestFactory(factory);
  }
}
