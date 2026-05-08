package backend.searchbyimage.service;

import backend.searchbyimage.dto.ImageSearchResponse;
import backend.searchbyimage.dto.ProductSearchResult;
import backend.searchbyimage.search.model.ProductSearchPage;
import backend.searchbyimage.search.repository.ProductSearchRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@Service
public class ImageSearchService {

    private static final Logger log = LoggerFactory.getLogger(ImageSearchService.class);

    private final ClipEmbeddingService clipEmbeddingService;
    private final ProductSearchRepository productSearchRepository;
    private final ProductSearchResultMapper productSearchResultMapper;
    private final JdbcTemplate jdbcTemplate;

    @Value("${image-search.default-per-page:50}")
    private int defaultPerPage;

    @Value("${image-search.max-per-page:200}")
    private int maxPerPage;

    @Value("${image-search.similarity-threshold:0.5}")
    private double similarityThreshold;

    public ImageSearchService(
            ClipEmbeddingService clipEmbeddingService,
            ProductSearchRepository productSearchRepository,
            ProductSearchResultMapper productSearchResultMapper,
            JdbcTemplate jdbcTemplate) {
        this.clipEmbeddingService = clipEmbeddingService;
        this.productSearchRepository = productSearchRepository;
        this.productSearchResultMapper = productSearchResultMapper;
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Search for similar products by uploading an image file.
     */
    @Transactional(readOnly = true)
    public ImageSearchResponse searchByImage(
            MultipartFile file,
            Integer page,
            Integer perPage,
            Integer limit,
            Double threshold) throws IOException {
        List<Float> embedding = clipEmbeddingService.getEmbeddingFromFile(file);
        return searchByEmbedding(embedding, page, perPage, limit, threshold);
    }

    /**
     * Search for similar products by providing an image URL.
     */
    @Transactional(readOnly = true)
    public ImageSearchResponse searchByImageUrl(
            String imageUrl,
            Integer page,
            Integer perPage,
            Integer limit,
            Double threshold) {
        List<Float> embedding = clipEmbeddingService.getEmbeddingFromUrl(imageUrl);
        return searchByEmbedding(embedding, page, perPage, limit, threshold);
    }

    /**
     * Core search: query pgvector for similar embeddings and build response.
     */
    private ImageSearchResponse searchByEmbedding(
            List<Float> embedding,
            Integer page,
            Integer perPage,
            Integer limit,
            Double threshold) {
        int currentPage = effectivePage(page);
        int effectivePerPage = effectivePerPage(perPage, limit);
        double effectiveThreshold = (threshold != null) ? threshold : similarityThreshold;

        String vectorString = ClipEmbeddingService.toVectorString(embedding);

        ProductSearchPage searchPage = productSearchRepository
                .searchSimilarProducts(vectorString, effectiveThreshold, currentPage, effectivePerPage);
        List<ProductSearchResult> searchResults = searchPage.rows()
                .stream()
                .map(row -> productSearchResultMapper.toResult(row, row.score()))
                .toList();

        return new ImageSearchResponse(
                searchResults,
                searchPage.totalResults(),
                currentPage,
                effectivePerPage,
                lastPage(searchPage.totalResults(), effectivePerPage)
        );
    }

    private int effectivePage(Integer page) {
        return page != null && page > 0 ? page : 1;
    }

    private int effectivePerPage(Integer perPage, Integer limit) {
        Integer requestedSize = perPage != null ? perPage : limit;
        if (requestedSize == null || requestedSize <= 0) {
            return defaultPerPage;
        }
        return Math.min(requestedSize, maxPerPage);
    }

    private int lastPage(int total, int perPage) {
        if (total <= 0) {
            return 0;
        }
        return (int) Math.ceil((double) total / perPage);
    }

    /**
     * Index a single product: download its image, generate embedding, store it.
     */
    public boolean indexProduct(String productId) {
        try {
            return indexSingleProduct(productId);
        } catch (Exception e) {
            log.error("Failed to index product {}: {}", productId, e.getMessage());
            return false;
        }
    }

    /**
     * Batch index products that don't have embeddings yet.
     */
    public int indexBatch(int batchSize) {
        List<String> productIds = jdbcTemplate.queryForList("""
                SELECT p.id::text
                FROM products p
                LEFT JOIN product_embeddings pe ON pe.product_id = p.id
                WHERE p.image_url IS NOT NULL
                  AND p.image_url <> ''
                  AND pe.product_id IS NULL
                ORDER BY p.updated_at DESC NULLS LAST, p.created_at DESC NULLS LAST
                LIMIT ?
                """, String.class, batchSize);
        int indexed = 0;

        for (String productId : productIds) {
            if (indexProduct(productId)) {
                indexed++;
            }
        }

        log.info("Batch indexed {}/{} products", indexed, productIds.size());
        return indexed;
    }

    /**
     * Index ALL products that don't have embeddings yet, in batches.
     */
    public int indexAll(int batchSize) {
        int totalIndexed = 0;
        int batchIndexed;

        do {
            batchIndexed = indexBatch(batchSize);
            totalIndexed += batchIndexed;
            log.info("Index all progress: {} total indexed so far", totalIndexed);
        } while (batchIndexed > 0);

        log.info("Index all complete: {} products indexed", totalIndexed);
        return totalIndexed;
    }

    private boolean indexSingleProduct(String productId) {
        ProductImageSource product = jdbcTemplate.query("""
                        SELECT p.id::text, p.image_url
                        FROM products p
                        WHERE p.id = CAST(? AS uuid)
                        LIMIT 1
                        """,
                (rs, rowNum) -> new ProductImageSource(rs.getString("id"), rs.getString("image_url")),
                productId)
                .stream()
                .findFirst()
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        if (product.imageUrl() == null || product.imageUrl().isBlank()) {
            log.warn("Product {} has no image, skipping embedding", productId);
            return false;
        }

        Integer existing = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM product_embeddings
                WHERE product_id = CAST(? AS uuid)
                """, Integer.class, productId);
        if (existing != null && existing > 0) {
            log.info("Product {} already has an embedding, skipping", productId);
            return false;
        }

        List<Float> embedding;
        try {
            embedding = clipEmbeddingService.getEmbeddingFromUrl(product.imageUrl());
        } catch (RuntimeException e) {
            throw e;
        }

        String vectorString = ClipEmbeddingService.toVectorString(embedding);

        int inserted = jdbcTemplate.update("""
                INSERT INTO product_embeddings (product_id, embedding, source_image_url, model_version, created_at)
                VALUES (CAST(? AS uuid), CAST(? AS vector), ?, 'clip-vit-base-patch32', NOW())
                ON CONFLICT (product_id) DO NOTHING
                """, productId, vectorString, product.imageUrl());
        if (inserted > 0) {
            log.info("Indexed product {} with embedding", productId);
            return true;
        }

        log.info("Product {} was indexed by another process, skipping", productId);
        return false;
    }

    private record ProductImageSource(String id, String imageUrl) {
    }
}
