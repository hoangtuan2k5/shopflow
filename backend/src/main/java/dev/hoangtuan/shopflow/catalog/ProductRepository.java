package dev.hoangtuan.shopflow.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

interface ProductRepository extends JpaRepository<Product, Long> {

  @Query(
      value =
          """
          SELECT p.id, p.name, p.description, p.price, p.image_key,
                 CASE WHEN COALESCE(i.on_hand_stock - i.reserved_stock, 0) > 0
                      THEN 'IN_STOCK' ELSE 'OUT_OF_STOCK' END AS stock_status
          FROM shopflow.products p
          LEFT JOIN shopflow.inventory_items i ON i.product_id = p.id
          WHERE p.active = TRUE
            AND p.id > :after
            AND LOWER(p.name) LIKE :pattern ESCAPE '!'
          ORDER BY p.id
          """,
      nativeQuery = true)
  List<ProductView> findActivePage(
      @Param("after") long after, @Param("pattern") String pattern, Pageable pageable);

  @Query(
      value =
          """
          SELECT p.id, p.name, p.description, p.price, p.image_key,
                 CASE WHEN COALESCE(i.on_hand_stock - i.reserved_stock, 0) > 0
                      THEN 'IN_STOCK' ELSE 'OUT_OF_STOCK' END AS stock_status
          FROM shopflow.products p
          LEFT JOIN shopflow.inventory_items i ON i.product_id = p.id
          WHERE p.id = :id AND p.active = TRUE
          """,
      nativeQuery = true)
  Optional<ProductView> findActiveById(@Param("id") Long id);

  @Query(
      value = "SELECT * FROM shopflow.products WHERE id = :id AND active = TRUE FOR UPDATE",
      nativeQuery = true)
  Optional<Product> findActiveForUpdate(@Param("id") Long id);

  interface ProductView {
    String getImageKey();

    Long getId();

    String getName();

    String getDescription();

    BigDecimal getPrice();

    String getStockStatus();
  }
}
