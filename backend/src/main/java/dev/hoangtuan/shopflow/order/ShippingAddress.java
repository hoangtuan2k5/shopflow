package dev.hoangtuan.shopflow.order;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

@Embeddable
class ShippingAddress {

  @Column(name = "receiver_name", nullable = false, length = 255)
  private String receiverName;

  @Column(name = "receiver_phone", nullable = false, length = 20)
  private String phone;

  @Column(name = "address_line", nullable = false, length = 500)
  private String addressLine;

  @Column(name = "district", length = 100)
  private String district;

  @Column(name = "city", nullable = false, length = 100)
  private String city;

  protected ShippingAddress() {}

  ShippingAddress(
      String receiverName, String phone, String addressLine, String district, String city) {
    this.receiverName = receiverName;
    this.phone = phone;
    this.addressLine = addressLine;
    this.district = district;
    this.city = city;
  }

  String getReceiverName() {
    return receiverName;
  }

  String getPhone() {
    return phone;
  }

  String getAddressLine() {
    return addressLine;
  }

  String getDistrict() {
    return district;
  }

  String getCity() {
    return city;
  }
}
