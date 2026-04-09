package backend.searchbyimage.search.model;

public record ProductSearchFilter(
        String platform,
        String category,
        String shop,
        Double minPrice,
        Double maxPrice,
        String sort,
        Integer page,
        Integer size
) {
    public int effectivePage() {
        return page != null && page >= 0 ? page : 0;
    }

    public int effectiveSize(int defaultSize, int maxSize) {
        if (size == null || size <= 0) {
            return defaultSize;
        }
        return Math.min(size, maxSize);
    }

    public String effectiveSort() {
        return sort == null || sort.isBlank() ? "relevance" : sort.trim().toLowerCase();
    }
}
