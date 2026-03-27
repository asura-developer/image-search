package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "variant_options",
        indexes = @Index(name = "idx_variant_options_variant", columnList = "variant_id"))
public class VariantOption {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "variant_id", nullable = false)
    private ProductVariant variant;

    @Setter
    @Column(nullable = false, length = 500)
    private String value;

    @Setter
    @Column(name = "image_url", columnDefinition = "TEXT")
    private String imageUrl;

    @Setter
    @Column(length = 100)
    private String vid;

    public VariantOption() {}

    public VariantOption(ProductVariant variant, String value) {
        this.variant = variant;
        this.value = value;
    }
}

