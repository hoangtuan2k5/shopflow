package dev.hoangtuan.shopflow.catalog;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.MemoryCacheImageInputStream;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.web.multipart.MultipartFile;

@Service
class ProductImageService {
  private final ProductRepository products;
  private final ProductImageStorage storage;

  ProductImageService(ProductRepository products, ProductImageStorage storage) {
    this.products = products;
    this.storage = storage;
  }

  @Transactional
  String upload(Long id, List<MultipartFile> files) throws IOException {
    if (files == null || files.size() != 1 || files.getFirst().isEmpty()) {
      throw new ProductImageException(400, "Exactly one non-empty image is required");
    }
    MultipartFile file = files.getFirst();
    if (file.getSize() > 2 * 1024 * 1024) {
      throw new ProductImageException(413, "Image exceeds 2 MiB limit");
    }
    byte[] bytes = file.getBytes();
    String extension = validate(bytes);
    Product product = products.findActiveForUpdate(id).orElseThrow(ProductNotFoundException::new);
    String previousKey = product.getImageKey();
    String key = "products/" + id + "/" + UUID.randomUUID() + "." + extension;
    TransactionSynchronizationManager.registerSynchronization(
        new TransactionSynchronization() {
          @Override
          public void afterCompletion(int status) {
            if (status == STATUS_COMMITTED) storage.delete(previousKey);
            else if (status == STATUS_ROLLED_BACK) storage.delete(key);
          }
        });
    storage.put(key, extension.equals("png") ? "image/png" : "image/jpeg", bytes);
    product.setImageKey(key);
    products.saveAndFlush(product);
    return storage.url(key);
  }

  private String validate(byte[] bytes) {
    boolean png =
        bytes.length >= 8
            && Arrays.equals(
                Arrays.copyOf(bytes, 8), new byte[] {(byte) 137, 80, 78, 71, 13, 10, 26, 10});
    boolean jpeg =
        bytes.length >= 3
            && (bytes[0] & 255) == 255
            && (bytes[1] & 255) == 216
            && (bytes[2] & 255) == 255;
    if (!png && !jpeg) throw unsupported();
    try (var input = new MemoryCacheImageInputStream(new ByteArrayInputStream(bytes))) {
      var readers = ImageIO.getImageReaders(input);
      if (!readers.hasNext()) throw unsupported();
      ImageReader reader = readers.next();
      try {
        reader.addIIOReadWarningListener(
            (source, warning) -> {
              throw unsupported();
            });
        reader.setInput(input, true, true);
        long pixels = (long) reader.getWidth(0) * reader.getHeight(0);
        if (pixels <= 0 || pixels > 16_000_000 || reader.read(0) == null) throw unsupported();
      } finally {
        reader.dispose();
      }
    } catch (IOException | IllegalArgumentException e) {
      throw unsupported();
    }
    return png ? "png" : "jpg";
  }

  private ProductImageException unsupported() {
    return new ProductImageException(
        415, "Only valid JPEG or PNG images up to 16 megapixels are supported");
  }
}
