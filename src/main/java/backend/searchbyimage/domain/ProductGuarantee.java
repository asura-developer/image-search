package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "product_guarantees",
        indexes = @Index(name = "idx_guarantees_product", columnList = "product_id"))
public class ProductGuarantee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "product_id", nullable = false)
    private Product product;

    @Setter
    @Column(nullable = false, length = 500)
    private String guarantee;

    public ProductGuarantee() {}

    public ProductGuarantee(Product product, String guarantee) {
        this.product = product;
        this.guarantee = guarantee;
    }
}

