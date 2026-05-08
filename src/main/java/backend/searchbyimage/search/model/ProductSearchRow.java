package backend.searchbyimage.search.model;

import java.math.BigDecimal;
import java.util.List;

public record ProductSearchRow(
        String id,
        String title,
        String productUrl,
        String imageUrl,
        String company,
        String categoryId,
        String categoryTitle,
        String categorySlug,
        BigDecimal originalPrice,
        Double score,
        String matchType,
        String highlight,
        List<String> matchedFields
) {
}
