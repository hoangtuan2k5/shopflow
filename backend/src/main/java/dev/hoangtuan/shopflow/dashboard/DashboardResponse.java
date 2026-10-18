package dev.hoangtuan.shopflow.dashboard;

import java.time.LocalDate;
import java.util.List;

record DashboardResponse(
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
  record Point(LocalDate date, String paidAmount, long orderCount) {}

  record StatusCount(String status, long count) {}

  record TopProduct(long productId, String productName, long quantity, String paidAmount) {}
}
