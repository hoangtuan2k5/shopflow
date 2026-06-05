package dev.hoangtuan.shopflow;

import org.springframework.boot.SpringApplication;

public class TestShopflowBackendApplication {

  public static void main(String[] args) {
    SpringApplication.from(ShopflowApplication::main)
        .with(TestcontainersConfiguration.class)
        .run(args);
  }
}
