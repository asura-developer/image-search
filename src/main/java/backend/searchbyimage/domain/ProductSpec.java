package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "product_specs",
        indexes = @Index(name = "idx_specs_product", columnList = "product_id"))
public class ProductSpec {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Setter
    @Column(name = "spec_key", nullable = false, length = 255)
    private String specKey;

    @Setter
    @Column(name = "spec_value", columnDefinition = "TEXT")
    private String specValue;

    public ProductSpec() {}

    public ProductSpec(Product product, String specKey, String specValue) {
        this.product = product;
        this.specKey = specKey;
        this.specValue = specValue;
    }
}

