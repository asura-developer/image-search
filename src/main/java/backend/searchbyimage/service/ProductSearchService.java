package backend.searchbyimage.service;

import backend.searchbyimage.domain.ProductImage;
import backend.searchbyimage.dto.ProductSearchResult;
import backend.searchbyimage.dto.ProductTextSearchResponse;
import backend.searchbyimage.repository.ProductImageRepository;
import backend.searchbyimage.search.ProductQueryNormalizer;
import backend.searchbyimage.search.model.NormalizedProductSearchQuery;
import backend.searchbyimage.search.model.ProductSearchPage;
import backend.searchbyimage.search.model.ProductSearchFilter;
import backend.searchbyimage.search.model.ProductSearchRow;
import backend.searchbyimage.search.repository.ProductSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class ProductSearchService {

    private static final Logger log = LoggerFactory.getLogger(ProductSearchService.class);

    private final ClipEmbeddingService clipEmbeddingService;
    private final ProductQueryNormalizer queryNormalizer;
    private final ProductSearchRepository productSearchRepository;
    private final ProductImageRepository productImageRepository;

    @Value("${product-search.default-limit:20}")
    private int defaultLimit;

    @Value("${product-search.max-limit:60}")
    private int maxLimit;

    @Value("${product-search.suggestion-limit:8}")
    private int suggestionLimit;

    public ProductSearchService(
            ClipEmbeddingService clipEmbeddingService,
            ProductQueryNormalizer queryNormalizer,
            ProductSearchRepository productSearchRepository,
            ProductImageRepository productImageRepository) {
        this.clipEmbeddingService = clipEmbeddingService;
        this.queryNormalizer = queryNormalizer;
        this.productSearchRepository = productSearchRepository;
        this.productImageRepository = productImageRepository;
    }

    @Transactional(readOnly = true)
    public ProductTextSearchResponse searchProducts(
            String rawQuery,
            String platform,
            String category,
            String shop,
            Double minPrice,
            Double maxPrice,
            String sort,
            Integer page,
            Integer size) {
        ProductSearchFilter filter = new ProductSearchFilter(
                platform,
                category,
                shop,
                minPrice,
                maxPrice,
                sort,
                page,
                size
        );

        NormalizedProductSearchQuery query = queryNormalizer.normalize(rawQuery, filter, defaultLimit, maxLimit);
        if (!StringUtils.hasText(query.normalized()) || query.tokens().isEmpty()) {
            return ProductTextSearchResponse.builder()
                    .query(rawQuery)
                    .normalizedQuery("")
                    .page(query.page())
                    .size(query.size())
                    .totalResults(0)
                    .hasMore(false)
                    .suggestions(List.of())
                    .results(List.of())
                    .build();
        }

        String queryEmbedding = buildQueryEmbedding(query.normalized());
        ProductSearchPage searchPage = productSearchRepository.searchProducts(query, queryEmbedding);
        List<ProductSearchRow> rows = searchPage.rows();
        Map<Long, List<String>> imageUrlsByProductId = loadImages(rows);

        List<ProductSearchResult> results = rows.stream()
                .map(row -> ProductSearchResult.builder()
                        .productId(row.productId())
                        .itemId(row.itemId())
                        .title(row.title())
                        .price(row.price())
                        .image(row.image())
                        .link(row.link())
                        .salesCount(row.salesCount())
                        .location(row.location())
                        .shopName(row.shopName())
                        .platformName(row.platformName())
                        .categoryName(row.categoryName())
                        .score(round(row.score()))
                        .matchType(row.matchType())
                        .highlight(row.highlight())
                        .matchedFields(row.matchedFields())
                        .imageUrls(imageUrlsByProductId.getOrDefault(row.productId(), List.of()))
                        .build())
                .toList();

        List<String> suggestions = results.isEmpty()
                ? productSearchRepository.fetchSuggestions(query.normalized(), suggestionLimit)
                : List.of();

        return ProductTextSearchResponse.builder()
                .query(rawQuery)
                .normalizedQuery(query.normalized())
                .page(query.page())
                .size(query.size())
                .totalResults(searchPage.totalResults())
                .hasMore(searchPage.hasMore())
                .suggestions(suggestions)
                .results(results)
                .build();
    }

    private String buildQueryEmbedding(String normalizedQuery) {
        try {
            return ClipEmbeddingService.toVectorString(clipEmbeddingService.getEmbeddingFromText(normalizedQuery));
        } catch (RuntimeException e) {
            log.warn("Falling back to lexical search because query embedding failed: {}", e.getMessage());
            return null;
        }
    }

    @Transactional(readOnly = true)
    public List<String> suggestions(String rawPrefix, Integer limit) {
        if (!StringUtils.hasText(rawPrefix)) {
            return List.of();
        }

        int effectiveLimit = limit != null && limit > 0
                ? Math.min(limit, suggestionLimit)
                : suggestionLimit;

        String normalized = queryNormalizer.normalize(
                rawPrefix,
                new ProductSearchFilter(null, null, null, null, null, null, 0, effectiveLimit),
                defaultLimit,
                maxLimit
        ).normalized();

        if (!StringUtils.hasText(normalized) || normalized.length() < 2) {
            return List.of();
        }

        return productSearchRepository.fetchSuggestions(normalized, effectiveLimit);
    }

    private Map<Long, List<String>> loadImages(List<ProductSearchRow> rows) {
        List<Long> productIds = rows.stream().map(ProductSearchRow::productId).distinct().toList();
        if (productIds.isEmpty()) {
            return Map.of();
        }

        List<ProductImage> images = productImageRepository.findByProductIdInOrderByProductIdAscSortOrderAsc(productIds);

        Map<Long, List<String>> imageUrlsByProductId = new LinkedHashMap<>();
        for (ProductImage image : images) {
            imageUrlsByProductId
                    .computeIfAbsent(image.getProduct().getId(), ignored -> new java.util.ArrayList<>())
                    .add(image.getUrl());
        }
        return imageUrlsByProductId;
    }

    private double round(Double score) {
        if (score == null) {
            return 0.0;
        }
        return Math.round(score * 1000.0) / 1000.0;
    }
}
