package dev.hoangtuan.shopflow.dto.dashboard;

import java.time.LocalDate;
import java.util.List;

public record DashboardResponse(
    LocalDate from,
    LocalDate to,
    String groupBy,
    String timeZone,
    String paidAmount,
    long orderCount,
    long processingOrders,
    long lowStockProducts,
    List<Point> series,
    List<StatusCount> statuses,
    List<TopProduct> topProducts) {
  public record Point(LocalDate date, String paidAmount, long orderCount) {}

  public record StatusCount(String status, long count) {}

  public record TopProduct(long productId, String productName, long quantity, String paidAmount) {}
}
