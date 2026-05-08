package backend.searchbyimage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "product_detail_variants")
public class ProductDetailVariant {

    @Id
    @Column(nullable = false)
    private UUID uuid;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_detail_id", nullable = false)
    private ProductDetail productDetail;

    @Column(name = "variant_index", nullable = false)
    private Integer variantIndex;

    @Column(name = "color_id")
    private UUID colorId;

    @Column(columnDefinition = "TEXT")
    private String color;

    @Column(name = "size_id")
    private UUID sizeId;

    @Column(columnDefinition = "TEXT")
    private String size;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "price_usd", precision = 12, scale = 2)
    private BigDecimal priceUsd;

    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Column(name = "stock_quantity")
    private Long stockQuantity;

    @Column(name = "stock_text", columnDefinition = "TEXT")
    private String stockText;

    @Column(name = "length_cm", precision = 12, scale = 2)
    private BigDecimal lengthCm;

    @Column(name = "width_cm", precision = 12, scale = 2)
    private BigDecimal widthCm;

    @Column(name = "height_cm", precision = 12, scale = 2)
    private BigDecimal heightCm;

    @Column(name = "volume_cm3", precision = 14, scale = 3)
    private BigDecimal volumeCm3;

    @Column(name = "weight_g", precision = 12, scale = 2)
    private BigDecimal weightG;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
