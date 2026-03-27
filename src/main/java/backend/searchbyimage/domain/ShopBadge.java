package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "shop_badges",
        uniqueConstraints = @UniqueConstraint(columnNames = {"shop_id", "badge"}),
        indexes = @Index(name = "idx_shop_badges_shop", columnList = "shop_id"))
public class ShopBadge {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Setter
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false)
    private Shop shop;

    @Setter
    @Column(nullable = false, length = 100)
    private String badge;

    public ShopBadge() {}

    public ShopBadge(Shop shop, String badge) {
        this.shop = shop;
        this.badge = badge;
    }
}

