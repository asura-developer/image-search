package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "order_status_history",
        indexes = {
                @Index(name = "idx_order_status_history_order", columnList = "order_id"),
                @Index(name = "idx_order_status_history_changed_at", columnList = "changed_at")
        })
public class OrderStatusHistory {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "order_id", nullable = false)
    private Order order;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private OrderStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", nullable = false, length = 30)
    private OrderStatus toStatus;

    @Column(name = "changed_by", length = 100)
    private String changedBy;

    @Column(length = 500)
    private String note;

    @Column(name = "changed_at", nullable = false, updatable = false)
    private LocalDateTime changedAt;

    // ─── Convenience accessor for GraphQL ────────────────────────────────────

    public Long getOrderId() {
        return order != null ? order.getId() : null;
    }

    @PrePersist
    void prePersist() {
        changedAt = LocalDateTime.now();
    }
}

