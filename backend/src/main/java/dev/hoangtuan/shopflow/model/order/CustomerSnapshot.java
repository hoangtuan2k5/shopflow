package dev.hoangtuan.shopflow.model.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
public class CustomerSnapshot {

  @Column(name = "customer_name", nullable = false, length = 255)
  private String fullName;

  @Column(name = "customer_phone", length = 20)
  private String phone;

  @Column(name = "customer_email", length = 255)
  private String email;

  protected CustomerSnapshot() {}

  public CustomerSnapshot(String fullName, String email, String phone) {
    this.fullName = fullName;
    this.email = email;
    this.phone = phone;
  }

  public String getFullName() {
    return fullName;
  }

  public String getEmail() {
    return email;
  }

  public String getPhone() {
    return phone;
  }
}
