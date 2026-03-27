package backend.searchbyimage.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@Builder
public class ProductSearchResult {

    private Long productId;
    private String itemId;
    private String title;
    private String price;
    private String image;
    private String link;
    private String salesCount;
    private String location;
    private String shopName;
    private String platformName;
    private String categoryName;
    private double similarity;
    private List<String> imageUrls;
}
