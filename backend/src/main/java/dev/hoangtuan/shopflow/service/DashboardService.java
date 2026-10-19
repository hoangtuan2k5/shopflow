package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.dashboard.DashboardResponse;
import dev.hoangtuan.shopflow.repository.jdbc.DashboardRepository;
import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class DashboardService {
  private static final ZoneId ZONE = ZoneId.of("Asia/Ho_Chi_Minh");

  private final DashboardRepository repository;

  public DashboardService(DashboardRepository repository) {
    this.repository = repository;
  }

  @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
  public DashboardResponse get(LocalDate from, LocalDate to, String groupBy) {
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
    for (var point : repository.series(start, end, groupBy)) {
      points.put(point.date(), point);
    }
    List<DashboardResponse.StatusCount> statuses = repository.statuses(start, end);
    var allStatuses = new ArrayList<DashboardResponse.StatusCount>();
    for (String status : List.of("PENDING_PAYMENT", "PAID", "PAYMENT_FAILED", "CANCELLED")) {
      allStatuses.add(
          statuses.stream()
              .filter(s -> s.status().equals(status))
              .findFirst()
              .orElse(new DashboardResponse.StatusCount(status, 0)));
    }
    List<DashboardResponse.TopProduct> top = repository.topProducts(start, end);
    long processing = repository.processingOrders();
    long lowStock = repository.lowStockProducts();
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
