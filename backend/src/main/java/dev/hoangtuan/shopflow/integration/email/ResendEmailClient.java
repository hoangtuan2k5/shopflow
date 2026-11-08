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
    return sendCode(recipient, displayName, otp, challengeId, "REGISTER");
  }

  public String sendResetCode(String recipient, String displayName, String otp, UUID challengeId) {
    return sendCode(recipient, displayName, otp, challengeId, "RESET");
  }

  public String sendSetupCode(String recipient, String displayName, String otp, UUID challengeId) {
    return sendCode(recipient, displayName, otp, challengeId, "SET");
  }

  public String sendPasswordChanged(String recipient, String displayName, UUID challengeId) {
    boolean vi = LocaleContextHolder.getLocale().getLanguage().equals("vi");
    String subject = "ShopFlow — " + (vi ? "Mật khẩu đã thay đổi" : "Password changed");
    String text =
        vi
            ? "Mật khẩu tài khoản của bạn đã được thay đổi. Nếu bạn không yêu cầu, hãy liên hệ hỗ trợ ShopFlow ngay."
            : "Your account password has been changed. If you did not request this, contact ShopFlow support immediately.";
    text = greeting(displayName, vi) + "\n\n" + text;
    return send(recipient, "password-changed/" + challengeId, subject, text, brandedHtml(text));
  }

  private String sendCode(
      String recipient, String displayName, String otp, UUID challengeId, String kind) {
    if (otp == null || !otp.matches("[0-9]{6}") || challengeId == null) {
      throw new IllegalArgumentException("A six-digit code and challenge ID are required");
    }
    boolean vi = LocaleContextHolder.getLocale().getLanguage().equals("vi");
    String purpose =
        kind.equals("REGISTER")
            ? (vi ? "Xác minh email" : "Verify email")
            : kind.equals("SET")
                ? (vi ? "Thiết lập mật khẩu" : "Set up password")
                : (vi ? "Đặt lại mật khẩu" : "Reset password");
    String greeting = greeting(displayName, vi);
    String instruction =
        kind.equals("REGISTER")
            ? (vi
                ? "Dùng mã dưới đây để xác minh email của bạn."
                : "Use this code to verify your email.")
            : kind.equals("SET")
                ? (vi
                    ? "Dùng mã dưới đây để thiết lập mật khẩu."
                    : "Use this code to set up your password.")
                : (vi
                    ? "Dùng mã dưới đây để đặt lại mật khẩu."
                    : "Use this code to reset your password.");
    String validity =
        vi
            ? "Mã có hiệu lực trong 10 phút và chỉ dùng một lần."
            : "This code expires in 10 minutes and can be used once.";
    String safety =
        vi ? "Không chia sẻ mã này với người khác." : "Do not share this code with anyone.";
    String ignore =
        vi
            ? "Nếu bạn không yêu cầu, hãy bỏ qua email này."
            : "If you did not request this, ignore this email.";
    String text =
        greeting
            + "\n\n"
            + instruction
            + "\n\n"
            + (vi ? "Mã xác minh: " : "Verification code: ")
            + otp
            + "\n\n"
            + validity
            + "\n"
            + safety
            + "\n\n"
            + ignore;
    String content =
        """
        <p style="margin:0 0 12px">%s</p>
        <p style="margin:0 0 24px">%s</p>
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="border-collapse:separate;margin:0 0 24px">
        <tr><td align="center" style="padding:24px 12px;background-color:#f3f6fa;border:1px solid #dfe6ef;border-radius:12px;text-align:center">
        <strong style="display:block;font-family:'Be Vietnam Pro',Arial,sans-serif;font-size:40px;line-height:56px;font-weight:700;letter-spacing:6px;color:#172b4d;white-space:nowrap">%s</strong>
        </td></tr></table>
        <p style="margin:0 0 8px">%s</p>
        <p style="margin:0 0 24px">%s</p>
        <p style="margin:0;padding-top:20px;border-top:1px solid #e5eaf0;color:#64748b;font-size:13px;line-height:21px">%s</p>
        """
            .formatted(
                HtmlUtils.htmlEscape(greeting),
                HtmlUtils.htmlEscape(instruction),
                otp,
                HtmlUtils.htmlEscape(validity),
                HtmlUtils.htmlEscape(safety),
                HtmlUtils.htmlEscape(ignore));
    return send(
        recipient,
        challengeId.toString(),
        "ShopFlow — " + purpose,
        text,
        emailHtml(purpose, content));
  }

  private static String greeting(String name, boolean vi) {
    return (vi ? "Xin chào" : "Hello") + (name == null || name.isBlank() ? "" : " " + name) + ",";
  }

  private static String brandedHtml(String text) {
    return emailHtml(
        "ShopFlow",
        "<p style=\"margin:0\">" + HtmlUtils.htmlEscape(text).replace("\n", "<br>") + "</p>");
  }

  private static String emailHtml(String title, String content) {
    return """
        <!doctype html><html><head><meta charset="UTF-8"><meta name="viewport" content="width=device-width,initial-scale=1">
        <style>@import url('https://fonts.googleapis.com/css2?family=Be+Vietnam+Pro:wght@400;700&display=swap');</style>
        <!--[if mso]><style>body,table,td,p,h1,strong{font-family:Arial,sans-serif!important}</style><![endif]-->
        </head><body style="margin:0;padding:0;background-color:#f3f6fa">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="background-color:#f3f6fa"><tr><td align="center" style="padding:24px 12px">
        <table role="presentation" width="100%%" cellpadding="0" cellspacing="0" style="max-width:600px;table-layout:fixed;background-color:#ffffff;border:1px solid #e5eaf0;border-radius:16px;font-family:'Be Vietnam Pro',Arial,sans-serif;color:#172033;font-size:15px;line-height:25px">
        <tr><td style="background-color:#ffffff;border-radius:16px 16px 0 0">
        <img src="cid:shopflow-banner" alt="ShopFlow" width="600" height="140" style="display:block;width:100%%;max-width:600px;height:auto;border:0">
        </td></tr><tr><td style="padding:12px 24px 28px;word-break:break-word">
        <h1 style="margin:0 0 24px;font-size:24px;line-height:34px;font-weight:700;color:#172b4d">%s</h1>%s
        </td></tr></table></td></tr></table></body></html>
        """
        .formatted(HtmlUtils.htmlEscape(title), content);
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
