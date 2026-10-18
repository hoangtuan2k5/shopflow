package dev.hoangtuan.shopflow.dashboard;

import java.time.LocalDate;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
class DashboardController {
  private final DashboardService service;

  DashboardController(DashboardService service) {
    this.service = service;
  }

  @GetMapping("/dashboard")
  DashboardResponse get(
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
      @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
      @RequestParam(defaultValue = "day") String groupBy) {
    return service.get(from, to, groupBy);
  }
}
