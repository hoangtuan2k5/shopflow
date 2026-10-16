package dev.hoangtuan.shopflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;

import java.net.CookieManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import software.amazon.awssdk.services.s3.S3Client;

@ActiveProfiles("test")
@SpringBootTest(
    webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
    properties =
        "spring.datasource.url=jdbc:h2:mem:imagehttp;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
class ProductImageHttpTests {
  @LocalServerPort int port;
  @Autowired S3Client storage;

  @Test
  void servletRejectsOversizeMultipartWithTheGlobalJsonHandler() throws Exception {
    var cookies = new CookieManager();
    try (var client = HttpClient.newBuilder().cookieHandler(cookies).build()) {
      URI login = URI.create("http://localhost:" + port + "/auth/login");
      var signedIn =
          client.send(
              HttpRequest.newBuilder(login)
                  .header("Content-Type", "application/json")
                  .POST(
                      HttpRequest.BodyPublishers.ofString(
                          "{\"username\":\"owner\",\"password\":\"Owner@2026\"}"))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(signedIn.statusCode()).isEqualTo(200);
      URI upload = URI.create("http://localhost:" + port + "/products/1/image");
      var rejected =
          client.send(
              HttpRequest.newBuilder(upload).POST(HttpRequest.BodyPublishers.noBody()).build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(rejected.statusCode()).isEqualTo(403);
      String csrf =
          cookies.getCookieStore().getCookies().stream()
              .filter(c -> c.getName().equals("XSRF-TOKEN"))
              .findFirst()
              .orElseThrow()
              .getValue();
      byte[] prefix =
          ("--image-test\r\nContent-Disposition: form-data; name=\"file\"; filename=\"test.png\"\r\n"
                  + "Content-Type: image/png\r\n\r\n")
              .getBytes(StandardCharsets.US_ASCII);
      byte[] suffix = "\r\n--image-test--\r\n".getBytes(StandardCharsets.US_ASCII);
      byte[] body = new byte[prefix.length + 2 * 1024 * 1024 + 1 + suffix.length];
      System.arraycopy(prefix, 0, body, 0, prefix.length);
      System.arraycopy(suffix, 0, body, body.length - suffix.length, suffix.length);
      var response =
          client.send(
              HttpRequest.newBuilder(upload)
                  .header("X-XSRF-TOKEN", csrf)
                  .header("Content-Type", "multipart/form-data; boundary=image-test")
                  .POST(HttpRequest.BodyPublishers.ofByteArray(body))
                  .build(),
              HttpResponse.BodyHandlers.ofString());
      assertThat(response.statusCode()).isEqualTo(413);
      assertThat(response.body()).contains("\"status\":413", "Image exceeds 2 MiB limit");
      verifyNoInteractions(storage);
    }
  }
}
