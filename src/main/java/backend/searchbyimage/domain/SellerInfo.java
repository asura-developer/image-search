package backend.searchbyimage.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Entity
@Table(name = "seller_info",
        indexes = @Index(name = "idx_seller_info_shop", columnList = "shop_id"))
public class SellerInfo {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @Setter
    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "shop_id", nullable = false, unique = true)
    private Shop shop;

    @Setter
    @Column(name = "positive_feedback_rate")
    private Integer positiveFeedbackRate;

    @Setter
    @Column(name = "has_vip")
    private Boolean hasVip = false;

    @Setter
    @Column(name = "avg_delivery_time", length = 100)
    private String avgDeliveryTime;

    @Setter
    @Column(name = "avg_refund_time", length = 100)
    private String avgRefundTime;

    public SellerInfo() {}
}

