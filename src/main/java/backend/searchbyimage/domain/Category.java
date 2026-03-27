package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "categories",
        indexes = {
                @Index(name = "idx_categories_platform", columnList = "platform_id"),
                @Index(name = "idx_categories_category_id", columnList = "category_id"),
                @Index(name = "idx_categories_name", columnList = "category_name")
        },
        uniqueConstraints = @UniqueConstraint(columnNames = {"platform_id", "category_id"}))
public class Category {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "platform_id", nullable = false)
    private Platform platform;

    @Setter
    @Column(name = "category_id", nullable = false, length = 100)
    private String categoryId;

    @Setter
    @Column(name = "category_name", length = 255)
    private String categoryName;

    @Column(name = "created_at")
    private OffsetDateTime createdAt;

    @Setter
    @Column(name = "updated_at")
    private OffsetDateTime updatedAt;

    @JsonIgnore
    @OneToMany(mappedBy = "category", fetch = FetchType.LAZY)
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

    public Category() {}

    public Category(Platform platform, String categoryId, String categoryName) {
        this.platform = platform;
        this.categoryId = categoryId;
        this.categoryName = categoryName;
    }
}

