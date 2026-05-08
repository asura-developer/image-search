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

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "subcategories")
public class Subcategory {

    @Id
    @Column(nullable = false)
    private UUID uuid;

    @Column(name = "sub_category_title", nullable = false, columnDefinition = "TEXT")
    private String subCategoryTitle;

    @Column(name = "sub_category_slug", nullable = false, unique = true, columnDefinition = "TEXT")
    private String subCategorySlug;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_id", nullable = false)
    private Category category;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
