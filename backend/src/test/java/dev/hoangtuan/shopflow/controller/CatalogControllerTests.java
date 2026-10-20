package dev.hoangtuan.shopflow.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import dev.hoangtuan.shopflow.TestcontainersConfiguration;
import java.util.ArrayList;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;

@ActiveProfiles("test")
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
@TestPropertySource(
    properties = "spring.jpa.database-platform=org.hibernate.dialect.PostgreSQLDialect")
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
    long inStockId = insertProduct("In stock", "Available product", "699000", true);
    long outOfStockId = insertProduct("Out of stock", null, "2190000", true);
    insertProduct("Inactive", null, "899000", false);
    insertInventory(inStockId, 5, 2);
    insertInventory(outOfStockId, 3, 3);

    mockMvc
        .perform(get("/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(2))
        .andExpect(jsonPath("$.items[0].id").value(inStockId))
        .andExpect(jsonPath("$.items[0].name").value("In stock"))
        .andExpect(jsonPath("$.items[0].price").value(699000.0))
        .andExpect(jsonPath("$.items[0].stockStatus").value("IN_STOCK"))
        .andExpect(jsonPath("$.items[1].id").value(outOfStockId))
        .andExpect(jsonPath("$.items[1].stockStatus").value("OUT_OF_STOCK"));
  }

  @Test
  void treatsMissingInventoryAsOutOfStock() throws Exception {
    long productId = insertProduct("No inventory", null, "1290000", true);

    mockMvc
        .perform(get("/products/{id}", productId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.stockStatus").value("OUT_OF_STOCK"));
  }

  @Test
  void returnsProductDetail() throws Exception {
    long productId = insertProduct("Product", "Description", "2190000", true);
    insertInventory(productId, 2, 1);

    mockMvc
        .perform(get("/products/{id}", productId))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.id").value(productId))
        .andExpect(jsonPath("$.name").value("Product"))
        .andExpect(jsonPath("$.description").value("Description"))
        .andExpect(jsonPath("$.price").value(2190000.0))
        .andExpect(jsonPath("$.stockStatus").value("IN_STOCK"));
  }

  @Test
  void returnsEmptyListWhenNoActiveProductsExist() throws Exception {
    insertProduct("Inactive", null, "899000", false);

    mockMvc
        .perform(get("/products"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.items.length()").value(0));
  }

  @Test
  void returnsNotFoundForMissingOrInactiveProduct() throws Exception {
    long inactiveId = insertProduct("Inactive", null, "899000", false);

    mockMvc
        .perform(get("/products/{id}", inactiveId))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.message").value("Product not found"))
        .andExpect(jsonPath("$.status").value(404));

    mockMvc.perform(get("/products/{id}", Long.MAX_VALUE)).andExpect(status().isNotFound());
  }

  @Test
  void loadsFortyFiveProductsInThreeBoundedBatches() throws Exception {
    var ids = new ArrayList<Long>();
    for (int i = 0; i < 45; i++) ids.add(insertProduct("Product " + i, null, "100000", true));
    insertProduct("Hidden product", null, "100000", false);
    mockMvc
        .perform(get("/products"))
        .andExpect(jsonPath("$.items.length()").value(20))
        .andExpect(jsonPath("$.items[0].id").value(ids.getFirst()))
        .andExpect(jsonPath("$.items[19].id").value(ids.get(19)))
        .andExpect(jsonPath("$.nextCursor").value(ids.get(19).toString()));
    mockMvc
        .perform(get("/products").param("after", ids.get(19).toString()))
        .andExpect(jsonPath("$.items.length()").value(20))
        .andExpect(jsonPath("$.items[0].id").value(ids.get(20)))
        .andExpect(jsonPath("$.items[19].id").value(ids.get(39)))
        .andExpect(jsonPath("$.nextCursor").value(ids.get(39).toString()));
    mockMvc
        .perform(get("/products").param("after", ids.get(39).toString()))
        .andExpect(jsonPath("$.items.length()").value(5))
        .andExpect(jsonPath("$.items[0].id").value(ids.get(40)))
        .andExpect(jsonPath("$.items[4].id").value(ids.getLast()))
        .andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void searchesBeyondTheFirstBatchAndKeepsDiacriticsAndLiteralWildcards() throws Exception {
    for (int i = 0; i < 25; i++) insertProduct("Other " + i, null, "100000", true);
    long match = insertProduct("Máy LỌC Sharp 100%_!", null, "100000", true);
    insertProduct("Máy lọc Sharp 100XX", null, "100000", true);
    mockMvc
        .perform(get("/products").param("q", "  máy lọc sharp 100%_!  "))
        .andExpect(jsonPath("$.items.length()").value(1))
        .andExpect(jsonPath("$.items[0].id").value(match))
        .andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()));
    mockMvc
        .perform(get("/products").param("q", "may loc"))
        .andExpect(jsonPath("$.items.length()").value(0));
    mockMvc
        .perform(get("/products").param("q", "%_!"))
        .andExpect(jsonPath("$.items.length()").value(1));
    mockMvc
        .perform(get("/products").param("q", "  "))
        .andExpect(jsonPath("$.items.length()").value(20));
  }

  @Test
  void honorsTheMaximumBatchSizeAndDeletedCursorBoundary() throws Exception {
    long boundary = insertProduct("Boundary", null, "100000", true);
    long next = insertProduct("Next", null, "100000", true);
    for (int i = 0; i < 100; i++) insertProduct("More " + i, null, "100000", true);
    jdbcTemplate.update("DELETE FROM shopflow.products WHERE id = ?", boundary);
    mockMvc
        .perform(get("/products").param("after", Long.toString(boundary)).param("limit", "100"))
        .andExpect(jsonPath("$.items.length()").value(100))
        .andExpect(jsonPath("$.items[0].id").value(next))
        .andExpect(jsonPath("$.nextCursor").isString());
    mockMvc
        .perform(get("/products").param("after", Long.toString(Long.MAX_VALUE)))
        .andExpect(jsonPath("$.items.length()").value(0))
        .andExpect(jsonPath("$.nextCursor").value(org.hamcrest.Matchers.nullValue()));
  }

  @Test
  void rejectsInvalidPaginationAndSearchParameters() throws Exception {
    String[][] cases = {
      {"limit", "0"},
      {"limit", "101"},
      {"limit", "-1"},
      {"limit", "bad"},
      {"after", "-1"},
      {"after", "bad"},
      {"after", "9223372036854775808"},
      {"q", "x".repeat(256)}
    };
    for (String[] query : cases) {
      mockMvc
          .perform(get("/products").param(query[0], query[1]))
          .andExpect(status().isBadRequest())
          .andExpect(jsonPath("$.message").value("Invalid catalog query parameters"))
          .andExpect(jsonPath("$.status").value(400));
    }
  }

  private long insertProduct(String name, String description, String price, boolean active) {
    jdbcTemplate.update(
        "INSERT INTO shopflow.products (name, description, price, active) VALUES (?, ?, ?, ?)",
        name,
        description,
        new java.math.BigDecimal(price),
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
