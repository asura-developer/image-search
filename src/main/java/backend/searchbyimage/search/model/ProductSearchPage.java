package backend.searchbyimage.search.model;

import java.util.List;

public record ProductSearchPage(
        List<ProductSearchRow> rows,
        boolean hasMore,
        int totalResults
) {
}
