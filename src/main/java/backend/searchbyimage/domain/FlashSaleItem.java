package backend.searchbyimage.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "flash_sale_items",
        indexes = {
                @Index(name = "idx_flash_sale_items_sale",    columnList = "flash_sale_id"),
                @Index(name = "idx_flash_sale_items_product", columnList = "product_id")
        })
public class FlashSaleItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "flash_sale_id", nullable = false)
    private FlashSale flashSale;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(name = "sale_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal salePrice;

    @Column(name = "original_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal originalPrice;

    @Column(name = "flash_stock_limit", nullable = false)
    private Integer flashStockLimit;

    @Column(name = "sold_count", nullable = false)
    private Integer soldCount = 0;

    @Column(name = "limit_per_customer", nullable = false)
    private Integer limitPerCustomer = 1;

    // Optimistic locking
    @Version
    private Long version;

    // ─── Convenience accessors for GraphQL ───────────────────────────────────

    public Long getFlashSaleId() {
        return flashSale != null ? flashSale.getId() : null;
    }

    public Long getProductId() {
        return product != null ? product.getId() : null;
    }
}

