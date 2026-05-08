package backend.searchbyimage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "product_details")
public class ProductDetail {

    @Id
    @Column(nullable = false)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scrape_run_id")
    private ScrapeRun scrapeRun;

    @Column(nullable = false)
    private boolean success;

    @Column(name = "scraped_at")
    private OffsetDateTime scrapedAt;

    @Column(columnDefinition = "TEXT")
    private String url;

    @Column(name = "product_title", columnDefinition = "TEXT")
    private String productTitle;

    @Column(name = "page_title", columnDefinition = "TEXT")
    private String pageTitle;

    @Column(name = "supplier_name", columnDefinition = "TEXT")
    private String supplierName;

    @Column(name = "original_price", precision = 12, scale = 2)
    private BigDecimal originalPrice;

    @Column(name = "original_price_usd", precision = 12, scale = 2)
    private BigDecimal originalPriceUsd;

    @Column(name = "promotional_price", precision = 12, scale = 2)
    private BigDecimal promotionalPrice;

    @Column(name = "promotional_price_usd", precision = 12, scale = 2)
    private BigDecimal promotionalPriceUsd;

    @Column(name = "coupon_price", precision = 12, scale = 2)
    private BigDecimal couponPrice;

    @Column(name = "coupon_price_usd", precision = 12, scale = 2)
    private BigDecimal couponPriceUsd;

    @Column(name = "first_piece_estimated_price", precision = 12, scale = 2)
    private BigDecimal firstPieceEstimatedPrice;

    @Column(name = "first_piece_estimated_price_usd", precision = 12, scale = 2)
    private BigDecimal firstPieceEstimatedPriceUsd;

    @Column(name = "price_text", columnDefinition = "TEXT")
    private String priceText;

    @Column(name = "quantity_price_tiers", nullable = false, columnDefinition = "jsonb")
    private String quantityPriceTiers;

    @Column(name = "discount_rules", nullable = false, columnDefinition = "jsonb")
    private String discountRules;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String pricing;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String availability;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String variants;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String attributes;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String media;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String meta;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
