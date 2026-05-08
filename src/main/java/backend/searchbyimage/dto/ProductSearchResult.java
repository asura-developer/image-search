package backend.searchbyimage.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

@Getter
@Setter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class ProductSearchResult {

    private String id;
    private String title;
    private String productUrl;
    private String imageUrl;
    private String company;
    private BigDecimal originalPrice;
    private CategorySummary category;
    private Double similarity;

    @Getter
    @Setter
    @Builder
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public static class CategorySummary {
        private String id;
        private String title;
        private String slug;
    }
}
