package backend.searchbyimage.domain;

import backend.searchbyimage.search.index.IndexSyncListener;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@EntityListeners(IndexSyncListener.class)
@Entity
@Table(name = "products",
        indexes = {
                @Index(name = "idx_products_item_id", columnList = "item_id"),
                @Index(name = "idx_products_platform", columnList = "platform_id"),
                @Index(name = "idx_products_shop", columnList = "shop_id"),
                @Index(name = "idx_products_category", columnList = "category_fk"),
                @Index(name = "idx_products_details_scraped", columnList = "details_scraped")
        })
public class Product {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @NotBlank
    @Column(name = "item_id", nullable = false, unique = true, length = 100)
    private String itemId;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id")
    private Shop shop;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_fk")
    private Category category;

    @Setter
    @NotBlank
    @Size(max = 500)
    @Column(nullable = false, length = 500)
    private String title;

    @Setter
    @Column(length = 50)
    private String price;

    @Setter
    @Column(columnDefinition = "TEXT")
    private String image;

    @Setter
    @Column(nullable = false, columnDefinition = "TEXT")
    private String link;

    @Setter
    @Column(name = "sales_count", length = 50)
    private String salesCount;

    @Setter
    @Column(length = 255)
    private String location;

    @Setter
    @Column(name = "details_scraped")
    private Boolean detailsScraped = false;

    @Setter
    @Column(name = "details_scraped_at")
    private OffsetDateTime detailsScrapedAt;

    @Setter
    @Column(name = "extraction_quality")
    private Integer extractionQuality;

    @Setter
    @Column(name = "extracted_at")
    private OffsetDateTime extractedAt;

    @Setter
    @Column(name = "migration_version")
    private Integer migrationVersion = 2;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Setter
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    // ─── Relationships ────────────────────────────────────────────────────────
    // @JsonIgnore prevents LazyInitializationException when Jackson serializes
    // Product for Redis caching — these collections are resolved via DataLoaders.

    @JsonIgnore
    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ProductDetail detail;

    @JsonIgnore
    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ProductSearchMeta searchMeta;

    @JsonIgnore
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductImage> images = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductVariant> variants = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductSpec> specs = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ProductGuarantee> guarantees = new ArrayList<>();

    @JsonIgnore
    @OneToOne(mappedBy = "product", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private ProductEmbedding embedding;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Product() {}

    public Product(String itemId, Platform platform, String title, String link) {
        this.itemId = itemId;
        this.platform = platform;
        this.title = title;
        this.link = link;
    }
}

