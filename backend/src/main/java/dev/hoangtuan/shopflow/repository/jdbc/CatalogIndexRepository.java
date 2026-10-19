package dev.hoangtuan.shopflow.repository.jdbc;

import dev.hoangtuan.shopflow.model.search.ProductText;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class CatalogIndexRepository {
  private final JdbcTemplate jdbcTemplate;

  public CatalogIndexRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  public List<ProductText> findActiveProducts() {
    return jdbcTemplate.query(
        "SELECT id, name, description FROM shopflow.products WHERE active = TRUE ORDER BY id",
        (rs, row) ->
            new ProductText(rs.getLong("id"), rs.getString("name"), rs.getString("description")));
  }
}
