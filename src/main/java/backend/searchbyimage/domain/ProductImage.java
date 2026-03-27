package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "product_images",
        indexes = @Index(name = "idx_product_images_product", columnList = "product_id"))
public class ProductImage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Setter
    @Column(nullable = false, columnDefinition = "TEXT")
    private String url;

    @Setter
    @Column(name = "source_type", length = 20)
    private String sourceType = "gallery";

    @Setter
    @Column(name = "sort_order")
    private Integer sortOrder = 0;

    public ProductImage() {}

    public ProductImage(Product product, String url, String sourceType, int sortOrder) {
        this.product = product;
        this.url = url;
        this.sourceType = sourceType;
        this.sortOrder = sortOrder;
    }
}

