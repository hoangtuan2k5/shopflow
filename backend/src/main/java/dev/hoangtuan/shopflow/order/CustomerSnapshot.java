package dev.hoangtuan.shopflow.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class CustomerSnapshot {

  @Column(name = "customer_name", nullable = false, length = 255)
  private String fullName;

  @Column(name = "customer_phone", length = 20)
  private String phone;

  @Column(name = "customer_email", length = 255)
  private String email;

  protected CustomerSnapshot() {}

  CustomerSnapshot(String fullName, String email, String phone) {
    this.fullName = fullName;
    this.email = email;
    this.phone = phone;
  }

  String getFullName() {
    return fullName;
  }

  String getEmail() {
    return email;
  }

  String getPhone() {
    return phone;
  }
}
