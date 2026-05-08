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
@Table(name = "product_scrape_results")
public class ProductScrapeResult {

    @Id
    @Column(nullable = false)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "scrape_run_id", nullable = false)
    private ScrapeRun scrapeRun;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    private Integer page;

    private Integer position;

    @Column(precision = 12, scale = 2)
    private BigDecimal price;

    @Column(name = "price_usd", precision = 12, scale = 2)
    private BigDecimal priceUsd;

    @Column(columnDefinition = "TEXT")
    private String sold;

    @Column(name = "repeat_rate", columnDefinition = "TEXT")
    private String repeatRate;

    @Column(nullable = false, columnDefinition = "jsonb")
    private String badges;

    @Column(name = "raw_text", columnDefinition = "TEXT")
    private String rawText;

    @Column(name = "scrape_status", nullable = false, columnDefinition = "TEXT")
    private String scrapeStatus;

    @Column(name = "scraped_at", nullable = false)
    private OffsetDateTime scrapedAt;
}
