package dev.hoangtuan.shopflow.customerreturn;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Tag(name = "Returns")
@RestController
@RequestMapping("/returns")
class ReturnController {

  private final ReturnService returnService;

  ReturnController(ReturnService returnService) {
    this.returnService = returnService;
  }

  @GetMapping
  List<ReturnResponse> list() {
    return returnService.list();
  }

  @GetMapping("/orders")
  List<ReturnableOrderResponse> returnableOrders() {
    return returnService.returnableOrders();
  }

  @PostMapping
  @ResponseStatus(HttpStatus.CREATED)
  ReturnResponse create(@Valid @RequestBody CreateReturnRequest request) {
    return returnService.create(request);
  }

  @PatchMapping("/{returnId}")
  ReturnResponse update(
      @PathVariable Long returnId, @Valid @RequestBody UpdateReturnRequest request) {
    return returnService.update(returnId, request);
  }
}
