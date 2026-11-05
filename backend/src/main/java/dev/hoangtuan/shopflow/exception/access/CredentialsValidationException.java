package dev.hoangtuan.shopflow.exception.access;

import java.util.Map;

public class CredentialsValidationException extends RuntimeException {
  private final Map<String, String> fieldErrors;

  public CredentialsValidationException(Map<String, String> fieldErrors) {
    this.fieldErrors = Map.copyOf(fieldErrors);
  }

  public Map<String, String> fieldErrors() {
    return fieldErrors;
  }
}
