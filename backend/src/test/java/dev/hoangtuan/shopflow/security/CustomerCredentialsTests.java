package dev.hoangtuan.shopflow.security;

import static org.assertj.core.api.Assertions.*;

import dev.hoangtuan.shopflow.exception.access.CredentialsValidationException;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CustomerCredentialsTests {
  @Test
  void validatesUnicodeCompositionAndBcryptByteBoundaryWithoutTrimming() {
    for (String value : new String[] {"Abcdef1!", "Ábcdef1€", " Abcde1!", "Aa1!" + "x".repeat(68)})
      assertThat(CustomerCredentials.validNewPassword(value))
          .as("valid password boundary")
          .isTrue();
    for (String value :
        new String[] {
          "Abcde1!",
          "abcdef1!",
          "ABCDEF1!",
          "Abcdefg!",
          "Abcdef12",
          "Abcdef1 ",
          "Abcdef١!",
          "Aa1!" + "x".repeat(69),
          "Aa1!" + "é".repeat(35),
          "Aa1!abc\uD800"
        })
      assertThat(CustomerCredentials.validNewPassword(value))
          .as("invalid password boundary")
          .isFalse();
  }

  @Test
  void canonicalizesEmailWithoutRemovingDotsOrPlusTags() {
    assertThat(CustomerCredentials.email("  Buyer.Name+Tag@EXAMPLE.com "))
        .isEqualTo("buyer.name+tag@example.com");
    for (String value :
        new String[] {
          "é@example.com",
          "a..b@example.com",
          "a@-domain.com",
          "a@domain",
          "x".repeat(65) + "@example.com"
        })
      assertThatThrownBy(() -> CustomerCredentials.email(value))
          .isInstanceOf(CredentialsValidationException.class);
    assertThat(CustomerCredentials.registrationName("Abcdef1!", "  😀 ")).isEqualTo("😀");
  }

  @Test
  void bindsVerifierToGenerationPurposeEmailAndSession() {
    var verifier = new OtpVerifier("AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA=");
    UUID id = UUID.randomUUID();
    byte[] code = verifier.code("a@example.com", "REGISTER", id, "012345");
    assertThat(code).hasSize(32);
    assertThat(verifier.matches(code, verifier.code("a@example.com", "REGISTER", id, "012345")))
        .isTrue();
    assertThat(
            verifier.matches(code, verifier.code("a@example.com", "RESET_PASSWORD", id, "012345")))
        .isFalse();
    assertThat(verifier.matches(code, verifier.code("b@example.com", "REGISTER", id, "012345")))
        .isFalse();
    assertThat(
            verifier.matches(
                code, verifier.code("a@example.com", "REGISTER", UUID.randomUUID(), "012345")))
        .isFalse();
    assertThat(verifier.sessionBinding("first")).isNotEqualTo(verifier.sessionBinding("second"));
    assertThat(new OtpVerifier(""))
        .satisfies(
            v ->
                assertThatThrownBy(v::requireConfigured)
                    .isInstanceOf(
                        dev.hoangtuan.shopflow.exception.access.EmailUnavailableException.class));
  }
}
