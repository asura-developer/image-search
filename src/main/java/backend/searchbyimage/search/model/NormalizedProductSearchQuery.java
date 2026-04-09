package backend.searchbyimage.search.model;

import java.util.List;

public record NormalizedProductSearchQuery(
        String raw,
        String normalized,
        String tsQuery,
        String phraseQuery,
        String likeQuery,
        List<String> tokens,
        ProductSearchFilter filter,
        int page,
        int size
) {
}
