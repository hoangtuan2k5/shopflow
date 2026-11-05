package dev.hoangtuan.shopflow.security;

import dev.hoangtuan.shopflow.exception.access.CredentialsValidationException;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;

public final class CustomerCredentials {
  private static final Pattern EMAIL =
      Pattern.compile(
          "[a-z0-9!#$%&'*+/=?^_`{|}~-]+(?:\\.[a-z0-9!#$%&'*+/=?^_`{|}~-]+)*@"
              + "[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?(?:\\.[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?)+");

  private CustomerCredentials() {}

  public static String identifier(String value) {
    return value.trim().toLowerCase(Locale.ROOT);
  }

  public static String email(String value) {
    String email = value == null ? "" : identifier(value);
    int at = email.indexOf('@');
    if (email.length() > 254 || at < 1 || at > 64 || !EMAIL.matcher(email).matches()) {
      throw new CredentialsValidationException(Map.of("email", "Enter a valid email address"));
    }
    return email;
  }

  public static boolean validNewPassword(String password) {
    if (password == null
        || password.codePointCount(0, password.length()) < 8
        || password.getBytes(StandardCharsets.UTF_8).length > 72) return false;
    boolean upper = false, lower = false, digit = false, special = false;
    for (int cp : password.codePoints().toArray()) {
      if (cp >= 0xD800 && cp <= 0xDFFF) return false;
      int type = Character.getType(cp);
      upper |= type == Character.UPPERCASE_LETTER;
      lower |= type == Character.LOWERCASE_LETTER;
      digit |= cp >= '0' && cp <= '9';
      special |=
          switch (type) {
            case Character.CONNECTOR_PUNCTUATION,
                Character.DASH_PUNCTUATION,
                Character.START_PUNCTUATION,
                Character.END_PUNCTUATION,
                Character.INITIAL_QUOTE_PUNCTUATION,
                Character.FINAL_QUOTE_PUNCTUATION,
                Character.OTHER_PUNCTUATION,
                Character.MATH_SYMBOL,
                Character.CURRENCY_SYMBOL,
                Character.MODIFIER_SYMBOL,
                Character.OTHER_SYMBOL ->
                true;
            default -> false;
          };
    }
    return upper && lower && digit && special;
  }

  public static String registrationName(String password, String displayName) {
    Map<String, String> errors = new LinkedHashMap<>();
    if (!validNewPassword(password))
      errors.put(
          "password",
          "Use at least 8 characters with uppercase, lowercase, a number and a symbol; maximum 72 UTF-8 bytes");
    String name = displayName == null ? "" : displayName.trim();
    if (name.isEmpty()
        || name.codePointCount(0, name.length()) > 255
        || name.codePoints().anyMatch(cp -> cp >= 0xD800 && cp <= 0xDFFF)) {
      errors.put("displayName", "Enter a display name of 1 to 255 characters");
    }
    if (!errors.isEmpty()) throw new CredentialsValidationException(errors);
    return name;
  }
}
