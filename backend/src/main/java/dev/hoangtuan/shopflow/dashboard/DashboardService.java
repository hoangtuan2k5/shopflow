package dev.hoangtuan.shopflow.dashboard;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
class DashboardService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");
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
  private final JdbcTemplate jdbc;

  DashboardService(JdbcTemplate jdbc) {
    this.jdbc = jdbc;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  DashboardResponse get(LocalDate from, LocalDate to, String groupBy) {
    if ((from == null) != (to == null) || !(groupBy.equals("day") || groupBy.equals("month"))) {
      throw new IllegalArgumentException("Invalid date range or grouping");
    }
    if (from == null) {
      to = LocalDate.now(ZONE);
      from = to.minusDays(29);
    }
    if (from.isAfter(to)
        || ChronoUnit.DAYS.between(from, to) > 365
        || from.getYear() < 1
        || to.getYear() > 9999) {
      throw new IllegalArgumentException("Invalid date range");
    }
    Timestamp start = Timestamp.from(from.atStartOfDay(ZONE).toInstant());
    Timestamp end = Timestamp.from(to.plusDays(1).atStartOfDay(ZONE).toInstant());
    var points = new LinkedHashMap<LocalDate, DashboardResponse.Point>();
    LocalDate bucket = groupBy.equals("month") ? from.withDayOfMonth(1) : from;
    for (;
        !bucket.isAfter(to);
        bucket = groupBy.equals("month") ? bucket.plusMonths(1) : bucket.plusDays(1)) {
      points.put(bucket, new DashboardResponse.Point(bucket, "0", 0));
    }
    jdbc.query(
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
        rs -> {
          LocalDate date = rs.getDate("bucket").toLocalDate();
          points.put(
              date,
              new DashboardResponse.Point(
                  date, money(rs.getBigDecimal("amount")), rs.getLong("orders")));
        },
        start,
        end,
        groupBy,
        groupBy,
        start,
        end);
    List<DashboardResponse.StatusCount> statuses =
        jdbc.query(
            """
        SELECT status, COUNT(*) AS count FROM shopflow.orders
        WHERE created_at >= ? AND created_at < ? GROUP BY status
        """,
            (rs, row) ->
                new DashboardResponse.StatusCount(rs.getString("status"), rs.getLong("count")),
            start,
            end);
    var allStatuses = new ArrayList<DashboardResponse.StatusCount>();
    for (String status : List.of("PENDING_PAYMENT", "PAID", "PAYMENT_FAILED", "CANCELLED")) {
      allStatuses.add(
          statuses.stream()
              .filter(s -> s.status().equals(status))
              .findFirst()
              .orElse(new DashboardResponse.StatusCount(status, 0)));
    }
    List<DashboardResponse.TopProduct> top =
        jdbc.query(
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
    long processing =
        jdbc.queryForObject(
            "SELECT COUNT(*) FROM shopflow.orders WHERE status = 'PAID' AND delivery_status IN ('NONE','PREPARING','SHIPPED')",
            Long.class);
    long lowStock =
        jdbc.queryForObject(
            """
        SELECT COUNT(*) FROM shopflow.products p LEFT JOIN shopflow.inventory_items i ON i.product_id = p.id
        WHERE p.active AND p.low_stock_threshold IS NOT NULL
          AND COALESCE(i.on_hand_stock, 0) - COALESCE(i.reserved_stock, 0) <= p.low_stock_threshold
        """,
            Long.class);
    var series = List.copyOf(points.values());
    BigDecimal amount =
        series.stream()
            .map(p -> new BigDecimal(p.paidAmount()))
            .reduce(BigDecimal.ZERO, BigDecimal::add);
    return new DashboardResponse(
        from,
        to,
        groupBy,
        ZONE.getId(),
        money(amount),
        series.stream().mapToLong(DashboardResponse.Point::orderCount).sum(),
        processing,
        lowStock,
        series,
        allStatuses,
        top);
  }

  private static String money(BigDecimal value) {
    return value.toBigIntegerExact().toString();
  }
}
