package dev.hoangtuan.shopflow.integration.email;

import dev.hoangtuan.shopflow.exception.access.EmailUnavailableException;
import java.io.IOException;
import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.HtmlUtils;

@Component
public class ResendEmailClient {
  private final RestClient client;
  private final String apiKey;
  private final String from;

  public ResendEmailClient(
      RestClient.Builder builder,
      @Value("${shopflow.mail.base-url}") String baseUrl,
      @Value("${shopflow.mail.api-key}") String apiKey,
      @Value("${shopflow.mail.from}") String from) {
    var factory =
        new JdkClientHttpRequestFactory(
            HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3))
                .followRedirects(HttpClient.Redirect.NEVER)
                .build());
    factory.setReadTimeout(Duration.ofSeconds(3));
    this.client = builder.clone().baseUrl(baseUrl).requestFactory(factory).build();
    this.apiKey = apiKey;
    this.from = from;
  }

  public void requireConfigured() {
    if (apiKey.isBlank() || from.isBlank()) throw new EmailUnavailableException();
  }

  public String sendRegistrationCode(
      String recipient, String displayName, String otp, UUID challengeId) {
    return sendCode(recipient, displayName, otp, challengeId, true);
  }

  public String sendResetCode(String recipient, String otp, UUID challengeId) {
    return sendCode(recipient, "", otp, challengeId, false);
  }

  public String sendPasswordChanged(String recipient, UUID challengeId) {
    boolean vi = LocaleContextHolder.getLocale().getLanguage().equals("vi");
    String subject = "ShopFlow — " + (vi ? "Mật khẩu đã thay đổi" : "Password changed");
    String text =
        vi
            ? "Mật khẩu tài khoản của bạn đã được thay đổi. Nếu bạn không yêu cầu, hãy liên hệ hỗ trợ ShopFlow ngay."
            : "Your account password has been changed. If you did not request this, contact ShopFlow support immediately.";
    return send(recipient, "password-changed/" + challengeId, subject, text, brandedHtml(text));
  }

  private String sendCode(
      String recipient, String displayName, String otp, UUID challengeId, boolean registration) {
    if (otp == null || !otp.matches("[0-9]{6}") || challengeId == null) {
      throw new IllegalArgumentException("A six-digit code and challenge ID are required");
    }
    boolean vi = LocaleContextHolder.getLocale().getLanguage().equals("vi");
    String purpose =
        registration
            ? (vi ? "Xác minh email" : "Verify email")
            : (vi ? "Đặt lại mật khẩu" : "Reset password");
    String text =
        (vi
                ? """
          Xin chào %s,

          Mã xác minh: %s
          Mã có hạn 10 phút, chỉ dùng một lần. Có thể yêu cầu gửi lại sau 60 giây,
          trong hạn mức cho phép. Không chia sẻ mã với người khác.

          Nếu bạn không yêu cầu, hãy bỏ qua thư này.
          """
                : """
          Hello %s,

          Verification code: %s
          This code expires in 10 minutes and can be used once. You may request
          another code after 60 seconds, subject to limits. Do not share this code.

          If you did not request this, ignore this email.
          """)
            .formatted(displayName, otp);
    String html = brandedHtml(text);
    return send(recipient, challengeId.toString(), "ShopFlow — " + purpose, text, html);
  }

  private static String brandedHtml(String text) {
    return """
        <div style="font-family:Arial,sans-serif;color:#172033;line-height:1.6;max-width:600px;margin:auto;padding:24px;background-color:#ffffff">
        <img src="cid:shopflow-banner" alt="ShopFlow" width="600" height="140" style="display:block;width:100%%;max-width:600px;height:auto;margin-bottom:24px">
        <h1 style="font-size:24px">ShopFlow</h1><p>%s</p></div>
        """
        .formatted(HtmlUtils.htmlEscape(text).replace("\n", "<br>"));
  }

  private String send(
      String recipient, String idempotencyKey, String subject, String text, String html) {
    requireConfigured();
    try {
      var response =
          client
              .post()
              .uri("/emails")
              .contentType(MediaType.APPLICATION_JSON)
              .headers(headers -> headers.setBearerAuth(apiKey))
              .header("Idempotency-Key", idempotencyKey)
              .body(
                  Map.of(
                      "from",
                      from,
                      "to",
                      List.of(recipient),
                      "subject",
                      subject,
                      "text",
                      text,
                      "html",
                      html,
                      "attachments",
                      List.of(
                          Map.of(
                              "filename", "shopflow-banner.png",
                              "content_id", "shopflow-banner",
                              "content",
                                  Base64.getEncoder()
                                      .encodeToString(
                                          new ClassPathResource("email/shopflow-banner.png")
                                              .getContentAsByteArray())))))
              .retrieve()
              .onStatus(
                  status -> !status.is2xxSuccessful(),
                  (request, reply) -> {
                    throw new EmailUnavailableException();
                  })
              .body(SentEmail.class);
      if (response == null || response.id() == null) throw new EmailUnavailableException();
      return UUID.fromString(response.id()).toString();
    } catch (RuntimeException | IOException exception) {
      // Provider bodies and transport causes can contain credentials or message content.
      throw new EmailUnavailableException();
    }
  }

  private record SentEmail(String id) {}
}
