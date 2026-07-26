package dev.hoangtuan.shopflow.customerreturn;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.Authentication;
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

  /**
   * Phân quyền ở đây phụ thuộc nội dung request nên không diễn đạt được bằng quy tắc theo URL.
   *
   * <p>Kho chỉ được nhập lại hàng đã duyệt; duyệt hoặc từ chối một yêu cầu đổi trả là quyết định
   * thương mại của chủ shop (FR-10.9). Nếu chỉ gắn quyền ở mức đường dẫn, nhân viên kho sẽ tự duyệt
   * được đổi trả cho chính mình.
   */
  @PatchMapping("/{returnId}")
  ReturnResponse update(
      @PathVariable Long returnId,
      @Valid @RequestBody UpdateReturnRequest request,
      Authentication authentication) {
    if (request.toStatus() != ReturnStatus.RESTOCKED && !isShopOwner(authentication)) {
      throw new AccessDeniedException("Only a shop owner can approve or reject a return");
    }
    return returnService.update(returnId, request);
  }

  private static boolean isShopOwner(Authentication authentication) {
    return authentication != null
        && authentication.getAuthorities().stream()
            .anyMatch(authority -> "ROLE_SHOP_OWNER".equals(authority.getAuthority()));
  }
}
