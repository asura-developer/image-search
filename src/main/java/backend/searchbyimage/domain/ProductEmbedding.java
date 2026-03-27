package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "product_embeddings",
        indexes = @Index(name = "idx_product_embeddings_product", columnList = "product_id"))
public class ProductEmbedding {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Setter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    /**
     * CLIP embedding vector stored as a float array.
     * The pgvector extension column is created via native SQL migration.
     * In JPA we store/load it as a float[] and use native queries for similarity search.
     */
    @Setter
    @Column(name = "embedding", columnDefinition = "vector(512)")
    private String embedding;

    @Setter
    @Column(name = "source_image_url", columnDefinition = "TEXT")
    private String sourceImageUrl;

    @Setter
    @Column(name = "model_version", length = 50)
    private String modelVersion = "clip-vit-base-patch32";

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public ProductEmbedding() {}

    public ProductEmbedding(Product product, String embedding, String sourceImageUrl) {
        this.product = product;
        this.embedding = embedding;
        this.sourceImageUrl = sourceImageUrl;
    }
}
