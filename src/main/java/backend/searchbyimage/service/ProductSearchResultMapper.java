package backend.searchbyimage.service;

import backend.searchbyimage.dto.ProductSearchResult;
import backend.searchbyimage.search.model.ProductSearchRow;
import org.springframework.stereotype.Component;

@Component
public class ProductSearchResultMapper {

    public ProductSearchResult toResult(ProductSearchRow row) {
        return toResult(row, null);
    }

    public ProductSearchResult toResult(ProductSearchRow row, Double similarity) {
        return ProductSearchResult.builder()
                .id(row.id())
                .title(row.title())
                .productUrl(row.productUrl())
                .imageUrl(row.imageUrl())
                .company(row.company())
                .originalPrice(row.originalPrice())
                .category(category(row))
                .similarity(similarity == null ? null : round(similarity, 10000.0))
                .build();
    }

    private ProductSearchResult.CategorySummary category(ProductSearchRow row) {
        if (row.categoryId() == null && row.categoryTitle() == null && row.categorySlug() == null) {
            return null;
        }
        return ProductSearchResult.CategorySummary.builder()
                .id(row.categoryId())
                .title(row.categoryTitle())
                .slug(row.categorySlug())
                .build();
    }

    private double round(Double value, double scale) {
        if (value == null) {
            return 0.0;
        }
        return Math.round(value * scale) / scale;
    }
}
