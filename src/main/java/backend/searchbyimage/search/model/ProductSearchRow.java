package backend.searchbyimage.search.model;

import java.util.List;

public record ProductSearchRow(
        Long productId,
        String itemId,
        String title,
        String price,
        String image,
        String link,
        String salesCount,
        String location,
        String shopName,
        String platformName,
        String categoryName,
        Double score,
        String matchType,
        String highlight,
        List<String> matchedFields
) {
}
