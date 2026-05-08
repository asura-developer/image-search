package backend.searchbyimage.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;
import java.util.UUID;

@Getter
@Setter
@Entity
@Table(name = "categories")
public class Category {

    @Id
    @Column(name = "uuid", nullable = false)
    private UUID uuid;

    @Column(name = "category_title", nullable = false, columnDefinition = "TEXT")
    private String categoryTitle;

    @Column(nullable = false, unique = true, columnDefinition = "TEXT")
    private String slug;

    @Column(name = "icon_url", columnDefinition = "TEXT")
    private String iconUrl;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
