package backend.searchbyimage.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class ProductTextSearchResponse {

    private String query;
    private String normalizedQuery;
    private int page;
    private int size;
    private int totalResults;
    private boolean hasMore;
    private List<String> suggestions;
    private List<ProductSearchResult> results;
}
