package dev.hoangtuan.shopflow.catalog;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.List;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import org.springframework.web.util.WebUtils;

@RestController
class ProductImageController {
  private final ProductImageService service;

  ProductImageController(ProductImageService service) {
    this.service = service;
  }

  @PostMapping("/products/{id}/image")
  ImageResponse upload(
      @PathVariable Long id,
      HttpServletRequest request,
      @RequestParam(value = "file", required = false) List<MultipartFile> files)
      throws IOException {
    var multipart = WebUtils.getNativeRequest(request, MultipartHttpServletRequest.class);
    if (multipart == null) {
      throw new ProductImageException(
          415, "Only valid JPEG or PNG images up to 16 megapixels are supported");
    }
    if (multipart.getMultiFileMap().values().stream().mapToInt(List::size).sum() != 1) {
      throw new ProductImageException(400, "Exactly one non-empty image is required");
    }
    return new ImageResponse(service.upload(id, files));
  }

  record ImageResponse(String imageUrl) {}
}
