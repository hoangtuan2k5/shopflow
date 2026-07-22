package dev.hoangtuan.shopflow.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties =
        "spring.datasource.url=jdbc:h2:mem:demo_catalog;MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE")
@ActiveProfiles("test")
class DemoCatalogMigrationTests {

  @Autowired private JdbcTemplate jdbcTemplate;

  @Test
  void seedsThirtySixActiveProductsWithInventory() {
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shopflow.products WHERE active = TRUE", Integer.class))
        .isEqualTo(36);
    assertThat(
            jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM shopflow.inventory_items WHERE on_hand_stock > 0",
                Integer.class))
        .isEqualTo(36);
  }
}
