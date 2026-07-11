package dev.hoangtuan.shopflow.catalog;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
class CatalogControllerTests {

  @Autowired private MockMvc mockMvc;

  @Autowired private JdbcTemplate jdbcTemplate;

  @BeforeEach
  void cleanDatabase() {
    jdbcTemplate.update("DELETE FROM shopflow.inventory_items");
    jdbcTemplate.update("DELETE FROM shopflow.products");
  }

  @Test
  void listsOnlyActiveProductsWithComputedStockStatus() throws Exception {
    long inStockId = insertProduct("In stock", "Available product", "10.00", true);
    long outOfStockId = insertProduct("Out of stock", null, "20.00", true);
    insertProduct("Inactive", null, "30.00", false);
    insertInventory(inStockId, 5, 2);
    insertInventory(outOfStockId, 3, 3);

    mockMvc
        .perform(get("/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(2))
        .andExpect(jsonPath("$[0].id").value(inStockId))
        .andExpect(jsonPath("$[0].stockStatus").value("IN_STOCK"))
        .andExpect(jsonPath("$[1].id").value(outOfStockId))
        .andExpect(jsonPath("$[1].stockStatus").value("OUT_OF_STOCK"));
  }

  @Test
  void treatsMissingInventoryAsOutOfStock() throws Exception {
    long productId = insertProduct("No inventory", null, "10.00", true);

    mockMvc
        .perform(get("/products/{id}", productId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockStatus").value("OUT_OF_STOCK"));
  }

  @Test
  void returnsProductDetail() throws Exception {
    long productId = insertProduct("Product", "Description", "99.99", true);

    mockMvc
        .perform(get("/products/{id}", productId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(productId))
        .andExpect(jsonPath("$.name").value("Product"))
        .andExpect(jsonPath("$.description").value("Description"))
        .andExpect(jsonPath("$.price").value(99.99));
  }

  @Test
  void returnsEmptyListWhenNoActiveProductsExist() throws Exception {
    insertProduct("Inactive", null, "10.00", false);

    mockMvc
        .perform(get("/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.length()").value(0));
  }

  @Test
  void returnsNotFoundForMissingOrInactiveProduct() throws Exception {
    long inactiveId = insertProduct("Inactive", null, "10.00", false);

    mockMvc
        .perform(get("/products/{id}", inactiveId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Product not found"))
        .andExpect(jsonPath("$.status").value(404));

    mockMvc.perform(get("/products/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
  }

  private long insertProduct(String name, String description, String price, boolean active) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, description, price, active) VALUES (?, ?, ?, ?)",
        name,
        description,
        price,
        active);
    return jdbcTemplate.queryForObject("SELECT MAX(id) FROM shopflow.products", Long.class);
  }

  private void insertInventory(long productId, int onHandStock, int reservedStock) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.inventory_items "
            + "(product_id, on_hand_stock, reserved_stock) VALUES (?, ?, ?)",
        productId,
        onHandStock,
        reservedStock);
  }
}
