package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;

@Getter
@Entity
@Table(name = "product_variants",
        indexes = @Index(name = "idx_variants_product", columnList = "product_id"))
public class ProductVariant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Setter
    @Column(name = "variant_type", nullable = false, length = 255)
    private String variantType;

    @OneToMany(mappedBy = "variant", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private List<VariantOption> options = new ArrayList<>();

    public ProductVariant() {}

    public ProductVariant(Product product, String variantType) {
        this.product = product;
        this.variantType = variantType;
    }
}

