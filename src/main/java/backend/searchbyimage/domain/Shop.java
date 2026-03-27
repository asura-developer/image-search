package backend.searchbyimage.domain;

import backend.searchbyimage.search.index.IndexSyncListener;
import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@EntityListeners(IndexSyncListener.class)
@Entity
@Table(name = "shops",
        indexes = {
                @Index(name = "idx_shops_platform", columnList = "platform_id"),
                @Index(name = "idx_shops_name", columnList = "shop_name")
        },
        uniqueConstraints = @UniqueConstraint(columnNames = {"platform_id", "shop_name"}))
public class Shop {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @Setter
    @Column(name = "shop_name", length = 255)
    private String shopName;

    @Setter
    @Column(name = "shop_link", columnDefinition = "TEXT")
    private String shopLink;

    @Setter
    @Column(name = "shop_rating", precision = 3, scale = 2)
    private java.math.BigDecimal shopRating;

    @Setter
    @Column(name = "shop_location", length = 255)
    private String shopLocation;

    @Setter
    @Column(name = "shop_age", length = 100)
    private String shopAge;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Setter
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @JsonIgnore
    @OneToOne(mappedBy = "shop", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private SellerInfo sellerInfo;

    @JsonIgnore
    @OneToMany(mappedBy = "shop", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<ShopBadge> badges = new ArrayList<>();

    @JsonIgnore
    @OneToMany(mappedBy = "shop", fetch = FetchType.LAZY)
    private List<Product> products = new ArrayList<>();

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
        updatedAt = OffsetDateTime.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Shop() {}
}

