package backend.searchbyimage.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Entity
@Table(name = "scraping_jobs",
        indexes = {
                @Index(name = "idx_jobs_status", columnList = "status"),
                @Index(name = "idx_jobs_created_at", columnList = "created_at"),
                @Index(name = "idx_jobs_category_fk", columnList = "category_fk"),
                @Index(name = "idx_jobs_category_id", columnList = "category_id")
        })
public class ScrapingJob {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Setter
    @Column(name = "job_id", nullable = false, unique = true, length = 100)
    private String jobId;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id")
    private Platform platform;

    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_fk")
    private Category category;

    @Setter
    @Column(name = "search_type", length = 20)
    private String searchType;

    @Setter
    @Column(nullable = false, length = 20)
    private String status;

    @Setter
    @Column(length = 500)
    private String keyword;

    @Setter
    @Column(name = "category_id", length = 100)
    private String categoryId;

    @Setter
    @Column(name = "category_name", length = 255)
    private String categoryName;

    @Setter
    @Column(name = "max_products")
    private Integer maxProducts;

    @Setter
    @Column(name = "max_pages")
    private Integer maxPages;

    @Setter
    @Column(name = "current_page")
    private Integer currentPage = 0;

    @Setter
    @Column(name = "products_scraped")
    private Integer productsScraped = 0;

    @Setter
    @Column(name = "details_scraped")
    private Integer detailsScraped = 0;

    @Setter
    @Column(name = "details_failed")
    private Integer detailsFailed = 0;

    @Setter
    @Column(name = "total_products")
    private Integer totalProducts = 0;

    @Setter
    @Column(name = "updated_products")
    private Integer updatedProducts = 0;

    @Setter
    @Column(name = "error_message", columnDefinition = "TEXT")
    private String errorMessage;

    @Setter
    @Column(name = "started_at")
    private OffsetDateTime startedAt;

    @Setter
    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = OffsetDateTime.now();
    }

    public ScrapingJob() {}

    public ScrapingJob(String jobId, String status) {
        this.jobId = jobId;
        this.status = status;
    }
}

