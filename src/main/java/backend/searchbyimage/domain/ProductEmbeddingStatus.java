package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "product_embedding_status",
        indexes = {
                @Index(name = "idx_product_embedding_status_state", columnList = "status"),
                @Index(name = "idx_product_embedding_status_retry", columnList = "next_retry_at")
        })
public class ProductEmbeddingStatus {

    public enum Status {
        PENDING,
        SUCCESS,
        RETRY,
        BLOCKED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Setter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Setter
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Status status = Status.PENDING;

    @Setter
    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount = 0;

    @Setter
    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Setter
    @Column(name = "last_http_status")
    private Integer lastHttpStatus;

    @Setter
    @Column(name = "last_attempt_at")
    private OffsetDateTime lastAttemptAt;

    @Setter
    @Column(name = "last_success_at")
    private OffsetDateTime lastSuccessAt;

    @Setter
    @Column(name = "next_retry_at")
    private OffsetDateTime nextRetryAt;

    @Setter
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }
}
