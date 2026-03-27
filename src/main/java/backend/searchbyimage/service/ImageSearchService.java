package backend.searchbyimage.service;

import backend.searchbyimage.domain.Product;
import backend.searchbyimage.domain.ProductEmbedding;
import backend.searchbyimage.dto.ImageSearchResponse;
import backend.searchbyimage.dto.ProductSearchResult;
import backend.searchbyimage.repository.ProductEmbeddingRepository;
import backend.searchbyimage.repository.ProductImageRepository;
import backend.searchbyimage.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImageSearchService {

    private static final Logger log = LoggerFactory.getLogger(ImageSearchService.class);

    private final ClipEmbeddingService clipEmbeddingService;
    private final ProductEmbeddingRepository embeddingRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;

    @Value("${image-search.default-limit:20}")
    private int defaultLimit;

    @Value("${image-search.max-limit:100}")
    private int maxLimit;

    @Value("${image-search.similarity-threshold:0.5}")
    private double similarityThreshold;

    public ImageSearchService(
            ClipEmbeddingService clipEmbeddingService,
            ProductEmbeddingRepository embeddingRepository,
            ProductRepository productRepository,
            ProductImageRepository productImageRepository) {
        this.clipEmbeddingService = clipEmbeddingService;
        this.embeddingRepository = embeddingRepository;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
    }

    /**
     * Search for similar products by uploading an image file.
     */
    @Transactional(readOnly = true)
    public ImageSearchResponse searchByImage(MultipartFile file, Integer limit, Double threshold) throws IOException {
        List<Float> embedding = clipEmbeddingService.getEmbeddingFromFile(file);
        return searchByEmbedding(embedding, limit, threshold);
    }

    /**
     * Search for similar products by providing an image URL.
     */
    @Transactional(readOnly = true)
    public ImageSearchResponse searchByImageUrl(String imageUrl, Integer limit, Double threshold) {
        List<Float> embedding = clipEmbeddingService.getEmbeddingFromUrl(imageUrl);
        return searchByEmbedding(embedding, limit, threshold);
    }

    /**
     * Core search: query pgvector for similar embeddings and build response.
     */
    private ImageSearchResponse searchByEmbedding(List<Float> embedding, Integer limit, Double threshold) {
        int effectiveLimit = (limit != null && limit > 0) ? Math.min(limit, maxLimit) : defaultLimit;
        double effectiveThreshold = (threshold != null) ? threshold : similarityThreshold;

        String vectorString = ClipEmbeddingService.toVectorString(embedding);

        List<Object[]> results = embeddingRepository.findSimilarProducts(
                vectorString, effectiveThreshold, effectiveLimit);

        List<ProductSearchResult> searchResults = new ArrayList<>();

        for (Object[] row : results) {
            Long productId = ((Number) row[0]).longValue();
            double similarity = ((Number) row[1]).doubleValue();

            Product product = productRepository.findById(productId).orElse(null);
            if (product == null) continue;

            List<String> imageUrls = productImageRepository
                    .findByProductIdOrderBySortOrder(productId)
                    .stream()
                    .map(pi -> pi.getUrl())
                    .toList();

            ProductSearchResult result = ProductSearchResult.builder()
                    .productId(product.getId())
                    .itemId(product.getItemId())
                    .title(product.getTitle())
                    .price(product.getPrice())
                    .image(product.getImage())
                    .link(product.getLink())
                    .salesCount(product.getSalesCount())
                    .location(product.getLocation())
                    .similarity(Math.round(similarity * 10000.0) / 10000.0)
                    .imageUrls(imageUrls)
                    .build();

            // Eagerly loaded fields — safe because we're in a transaction
            if (product.getShop() != null) {
                result.setShopName(product.getShop().getShopName());
            }
            if (product.getPlatform() != null) {
                result.setPlatformName(product.getPlatform().getName());
            }
            if (product.getCategory() != null) {
                result.setCategoryName(product.getCategory().getCategoryName());
            }

            searchResults.add(result);
        }

        return new ImageSearchResponse(searchResults.size(), searchResults);
    }

    /**
     * Index a single product: download its image, generate embedding, store it.
     */
    @Transactional
    public void indexProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        if (product.getImage() == null || product.getImage().isBlank()) {
            log.warn("Product {} has no image, skipping embedding", productId);
            return;
        }

        if (embeddingRepository.existsByProductId(productId)) {
            log.info("Product {} already has an embedding, skipping", productId);
            return;
        }

        try {
            List<Float> embedding = clipEmbeddingService.getEmbeddingFromUrl(product.getImage());
            String vectorString = ClipEmbeddingService.toVectorString(embedding);

            embeddingRepository.insertEmbedding(
                    productId, vectorString, product.getImage(), "clip-vit-base-patch32");

            log.info("Indexed product {} with embedding", productId);
        } catch (Exception e) {
            log.error("Failed to index product {}: {}", productId, e.getMessage());
        }
    }

    /**
     * Batch index products that don't have embeddings yet.
     */
    @Transactional
    public int indexBatch(int batchSize) {
        List<Long> productIds = productRepository.findProductIdsWithoutEmbeddings(batchSize);
        int indexed = 0;

        for (Long productId : productIds) {
            try {
                indexProduct(productId);
                indexed++;
            } catch (Exception e) {
                log.error("Failed to index product {}: {}", productId, e.getMessage());
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
}
