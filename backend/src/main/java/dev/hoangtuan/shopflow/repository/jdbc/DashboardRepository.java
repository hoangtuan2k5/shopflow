package dev.hoangtuan.shopflow.repository.jdbc;

import dev.hoangtuan.shopflow.dto.dashboard.DashboardResponse;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class DashboardRepository {
  private final JdbcTemplate jdbcTemplate;

  public DashboardRepository(JdbcTemplate jdbcTemplate) {
    this.jdbcTemplate = jdbcTemplate;
  }

  private static final String PAID =
      """
      WITH paid_orders AS (
        SELECT o.id, o.total_amount, p.paid_at
        FROM shopflow.orders o
        JOIN (SELECT order_id, MIN(paid_at) AS paid_at
              FROM shopflow.payments WHERE status = 'SUCCESS'
              GROUP BY order_id) p ON p.order_id = o.id
        WHERE p.paid_at >= ? AND p.paid_at < ?
      )
      """;

  public List<DashboardResponse.Point> series(Timestamp start, Timestamp end, String groupBy) {
    return jdbcTemplate.query(
        PAID
            + """
        SELECT bucket, SUM(amount) AS amount, SUM(orders) AS orders FROM (
          SELECT date_trunc(?, paid_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date AS bucket,
                 SUM(total_amount) AS amount, 0 AS orders
          FROM paid_orders GROUP BY 1
          UNION ALL
          SELECT date_trunc(?, created_at AT TIME ZONE 'Asia/Ho_Chi_Minh')::date,
                 0, COUNT(*) FROM shopflow.orders
          WHERE created_at >= ? AND created_at < ? GROUP BY 1
        ) x GROUP BY bucket ORDER BY bucket
        """,
        (rs, row) ->
            new DashboardResponse.Point(
                rs.getDate("bucket").toLocalDate(),
                money(rs.getBigDecimal("amount")),
                rs.getLong("orders")),
        start,
        end,
        groupBy,
        groupBy,
        start,
        end);
  }

  public List<DashboardResponse.StatusCount> statuses(Timestamp start, Timestamp end) {
    return jdbcTemplate.query(
        """
        SELECT status, COUNT(*) AS count FROM shopflow.orders
        WHERE created_at >= ? AND created_at < ? GROUP BY status
        """,
        (rs, row) -> new DashboardResponse.StatusCount(rs.getString("status"), rs.getLong("count")),
        start,
        end);
  }

  public List<DashboardResponse.TopProduct> topProducts(Timestamp start, Timestamp end) {
    return jdbcTemplate.query(
        PAID
            + """
        SELECT i.product_id, (array_agg(i.product_name ORDER BY o.paid_at DESC, i.id DESC))[1] AS name,
               SUM(i.quantity) AS quantity, SUM(i.quantity * i.unit_price) AS amount
        FROM shopflow.order_items i JOIN paid_orders o ON o.id = i.order_id
        GROUP BY i.product_id ORDER BY quantity DESC, i.product_id ASC LIMIT 5
        """,
        (rs, row) ->
            new DashboardResponse.TopProduct(
                rs.getLong("product_id"),
                rs.getString("name"),
                rs.getLong("quantity"),
                money(rs.getBigDecimal("amount"))),
        start,
        end);
  }

  public long processingOrders() {
    return jdbcTemplate.queryForObject(
        "SELECT COUNT(*) FROM shopflow.orders WHERE status = 'PAID' AND delivery_status IN ('NONE','PREPARING','SHIPPED')",
        Long.class);
  }

  public long lowStockProducts() {
    return jdbcTemplate.queryForObject(
        """
        SELECT COUNT(*) FROM shopflow.products p LEFT JOIN shopflow.inventory_items i ON i.product_id = p.id
        WHERE p.active AND p.low_stock_threshold IS NOT NULL
          AND COALESCE(i.on_hand_stock, 0) - COALESCE(i.reserved_stock, 0) <= p.low_stock_threshold
        """,
        Long.class);
  }

  private static String money(BigDecimal value) {
    return value.toBigIntegerExact().toString();
  }
}
