package backend.searchbyimage.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "pre_orders",
        indexes = {
                @Index(name = "idx_pre_orders_customer", columnList = "customer_id"),
                @Index(name = "idx_pre_orders_product",  columnList = "product_id"),
                @Index(name = "idx_pre_orders_status",   columnList = "status")
        })
public class PreOrder {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "customer_id", nullable = false)
    private User customer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Column(nullable = false)
    private Integer quantity;

    @Column(name = "unit_price", nullable = false, precision = 19, scale = 4)
    private BigDecimal unitPrice;

    @Column(name = "deposit_amount", precision = 19, scale = 4)
    private BigDecimal depositAmount;

    @Column(name = "expected_release_date")
    private LocalDate expectedReleaseDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 30)
    private PreOrderStatus status = PreOrderStatus.PENDING_PREORDER;

    @Version
    @Column(nullable = false)
    private Long version = 0L;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    // ─── Convenience accessors for GraphQL ───────────────────────────────────

    public Long getCustomerId() {
        return customer != null ? customer.getId() : null;
    }

    public Long getProductId() {
        return product != null ? product.getId() : null;
    }

    // ─── Lifecycle ───────────────────────────────────────────────────────────

    @PrePersist
    void prePersist() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
    }

    @PreUpdate
    void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
}

