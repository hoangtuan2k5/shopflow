package dev.hoangtuan.shopflow.dto.access;

import com.fasterxml.jackson.annotation.JsonAnySetter;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record DeliveryDetails(
    @NotBlank @Size(max = 255) String receiverName,
    @NotBlank @Size(max = 20) String phone,
    @NotBlank @Size(max = 500) String addressLine,
    @Size(max = 100) String district,
    @NotBlank @Size(max = 100) String city) {
  public DeliveryDetails {
    receiverName = trim(receiverName);
    phone = trim(phone);
    addressLine = trim(addressLine);
    district = trim(district);
    city = trim(city);
  }

  private static String trim(String value) {
    return value == null ? null : value.trim();
  }

  @JsonAnySetter
  public void rejectUnknown(String name, Object value) {
    throw new IllegalArgumentException("Unknown field");
  }
}
