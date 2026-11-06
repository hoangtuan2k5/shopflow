package dev.hoangtuan.shopflow.security;

import dev.hoangtuan.shopflow.exception.access.EmailUnavailableException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class OtpVerifier {
  private final byte[] key;
  private final SecureRandom random = new SecureRandom();

  public OtpVerifier(@Value("${shopflow.auth.otp-hmac-secret}") String secret) {
    byte[] decoded;
    try {
      decoded = Base64.getDecoder().decode(secret);
    } catch (IllegalArgumentException invalid) {
      decoded = new byte[0];
    }
    key = decoded.length >= 32 ? decoded : null;
  }

  public void requireConfigured() {
    if (key == null) throw new EmailUnavailableException();
  }

  public String newCode() {
    return String.format(Locale.ROOT, "%06d", random.nextInt(1_000_000));
  }

  public byte[] sessionBinding(String sessionId) {
    return hmac("session\0" + sessionId);
  }

  public byte[] code(String email, String purpose, UUID generation, String otp) {
    return hmac("otp\0" + email + "\0" + purpose + "\0" + generation + "\0" + otp);
  }

  public boolean matches(byte[] expected, byte[] actual) {
    return expected != null && MessageDigest.isEqual(expected, actual);
  }

  private byte[] hmac(String value) {
    requireConfigured();
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      mac.init(new SecretKeySpec(key, "HmacSHA256"));
      return mac.doFinal(value.getBytes(StandardCharsets.UTF_8));
    } catch (GeneralSecurityException impossible) {
      throw new IllegalStateException("HMAC-SHA256 unavailable");
    }
  }
}
