package dev.hoangtuan.shopflow.service;

import dev.hoangtuan.shopflow.dto.receiving.ReceivingRequest;
import dev.hoangtuan.shopflow.dto.receiving.ReceivingResponse;
import dev.hoangtuan.shopflow.exception.receiving.ReceivingConflictException;
import dev.hoangtuan.shopflow.exception.receiving.ReceivingNotFoundException;
import dev.hoangtuan.shopflow.exception.receiving.ReceivingValidationException;
import dev.hoangtuan.shopflow.repository.jdbc.ReceivingRepository;
import dev.hoangtuan.shopflow.repository.jdbc.ReceivingRepository.InventoryRow;
import dev.hoangtuan.shopflow.repository.jdbc.ReceivingRepository.ProductRow;
import dev.hoangtuan.shopflow.repository.jdbc.ReceivingRepository.ReceivingRecord;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReceivingService {

  private final ReceivingRepository repository;

  public ReceivingService(ReceivingRepository repository) {
    this.repository = repository;
  }

  @Transactional
  public ReceivingResponse receive(ReceivingRequest request) {
    ProductRow product = repository.findProductForUpdate(request.productId());
    if (product == null) {
      throw new ReceivingNotFoundException();
    }

    String supplierName = optionalText(request.supplierName(), "supplierName", 255);
    String note = optionalText(request.note(), "note", 500);
    InventoryRow inventory = repository.findInventoryForUpdate(product.id());
    if (inventory == null) {
      repository.createInventory(product.id());
      inventory = new InventoryRow(0, 0);
    }

    long newOnHand = (long) inventory.onHandStock() + request.quantity();
    if (newOnHand > Integer.MAX_VALUE) {
      throw new ReceivingConflictException("Receiving exceeds inventory limit");
    }

    repository.updateStock(product.id(), (int) newOnHand);
    ReceivingRecord record =
        repository.insertReceiving(product.id(), request.quantity(), supplierName, note);
    repository.recordReceiving(product.id(), request.quantity(), record.id(), note);

    return new ReceivingResponse(
        record.id(),
        product.id(),
        product.name(),
        request.quantity(),
        supplierName,
        note,
        record.createdAt(),
        null,
        (int) newOnHand,
        inventory.reservedStock(),
        (int) newOnHand - inventory.reservedStock());
  }

  private String optionalText(String value, String field, int maximumLength) {
    if (value == null) {
      return null;
    }

    String text = value.strip();
    if (text.isEmpty()) {
      throw new ReceivingValidationException(field, "Must not be blank");
    }
    if (text.length() > maximumLength) {
      throw new ReceivingValidationException(
          field, "Must be %d characters or fewer".formatted(maximumLength));
    }
    return text;
  }
}
