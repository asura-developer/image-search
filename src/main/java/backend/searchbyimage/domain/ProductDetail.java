package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "product_details")
public class ProductDetail {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Setter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @Setter
    @Column(name = "full_title", length = 1000)
    private String fullTitle;

    @Setter
    @Column(name = "full_description", columnDefinition = "TEXT")
    private String fullDescription;

    @Setter
    @Column(length = 255)
    private String brand;

    @Setter
    @Column(length = 20)
    private String rating;

    @Setter
    @Column(name = "review_count", length = 50)
    private String reviewCount;

    @Setter
    @Column(name = "sales_volume", length = 50)
    private String salesVolume;

    @Setter
    @Column(name = "original_price", length = 50)
    private String originalPrice;

    @Setter
    @Column(name = "in_stock")
    private Boolean inStock;

    @Setter
    @Column(name = "shipping_info", columnDefinition = "TEXT")
    private String shippingInfo;

    // ─── Data Quality Flags ───────────────────────────────────────────────────

    @Setter
    @Column(name = "dq_has_title")
    private Boolean dqHasTitle;

    @Setter
    @Column(name = "dq_has_price")
    private Boolean dqHasPrice;

    @Setter
    @Column(name = "dq_has_images")
    private Boolean dqHasImages;

    @Setter
    @Column(name = "dq_has_variants")
    private Boolean dqHasVariants;

    @Setter
    @Column(name = "dq_has_specs")
    private Boolean dqHasSpecs;

    @Setter
    @Column(name = "dq_has_brand")
    private Boolean dqHasBrand;

    @Setter
    @Column(name = "dq_has_reviews")
    private Boolean dqHasReviews;

    @Setter
    @Column(name = "dq_has_description")
    private Boolean dqHasDescription;

    @Setter
    @Column(name = "dq_has_sales_volume")
    private Boolean dqHasSalesVolume;

    @Setter
    @Column(name = "dq_has_shop_name")
    private Boolean dqHasShopName;

    @Setter
    @Column(name = "dq_completeness")
    private Integer dqCompleteness;

    public ProductDetail() {}
}

