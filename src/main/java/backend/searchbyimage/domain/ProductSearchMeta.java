package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "product_search_meta",
        indexes = {
                @Index(name = "idx_search_meta_keyword", columnList = "search_keyword"),
                @Index(name = "idx_search_meta_category", columnList = "category_name"),
                @Index(name = "idx_search_meta_category_id", columnList = "category_id"),
                @Index(name = "idx_search_meta_category_fk", columnList = "category_fk")
        })
public class ProductSearchMeta {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Setter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false, unique = true)
    private Product product;

    @JsonIgnore
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "category_fk")
    private Category category;

    @Setter
    @Column(name = "search_keyword", length = 500)
    private String searchKeyword;

    @Setter
    @Column(name = "category_id", length = 100)
    private String categoryId;

    @Setter
    @Column(name = "category_name", length = 255)
    private String categoryName;

    @Setter
    @Column(name = "page_number")
    private Integer pageNumber;

    public ProductSearchMeta() {}
}

