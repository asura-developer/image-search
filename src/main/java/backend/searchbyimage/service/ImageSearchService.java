package backend.searchbyimage.service;

import backend.searchbyimage.domain.Product;
import backend.searchbyimage.domain.ProductEmbeddingStatus;
import backend.searchbyimage.domain.ProductEmbedding;
import backend.searchbyimage.dto.ImageSearchResponse;
import backend.searchbyimage.dto.ProductSearchResult;
import backend.searchbyimage.repository.ProductEmbeddingRepository;
import backend.searchbyimage.repository.ProductEmbeddingStatusRepository;
import backend.searchbyimage.repository.ProductImageRepository;
import backend.searchbyimage.repository.ProductRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

@Service
public class ImageSearchService {

    private static final Logger log = LoggerFactory.getLogger(ImageSearchService.class);

    private final ClipEmbeddingService clipEmbeddingService;
    private final ProductEmbeddingRepository embeddingRepository;
    private final ProductEmbeddingStatusRepository embeddingStatusRepository;
    private final ProductRepository productRepository;
    private final ProductImageRepository productImageRepository;
    private final TransactionTemplate transactionTemplate;

    private static final int MAX_RETRYABLE_ATTEMPTS = 5;
    private static final int BLOCKED_STATUS_RETRY_DELAY_HOURS = 24;

    @Value("${image-search.default-limit:20}")
    private int defaultLimit;

    @Value("${image-search.max-limit:100}")
    private int maxLimit;

    @Value("${image-search.similarity-threshold:0.5}")
    private double similarityThreshold;

    public ImageSearchService(
            ClipEmbeddingService clipEmbeddingService,
            ProductEmbeddingRepository embeddingRepository,
            ProductEmbeddingStatusRepository embeddingStatusRepository,
            ProductRepository productRepository,
            ProductImageRepository productImageRepository,
            TransactionTemplate transactionTemplate) {
        this.clipEmbeddingService = clipEmbeddingService;
        this.embeddingRepository = embeddingRepository;
        this.embeddingStatusRepository = embeddingStatusRepository;
        this.productRepository = productRepository;
        this.productImageRepository = productImageRepository;
        this.transactionTemplate = transactionTemplate;
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
    public boolean indexProduct(Long productId) {
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
        List<Long> productIds = productRepository.findProductIdsWithoutEmbeddings(batchSize);
        int indexed = 0;

        for (Long productId : productIds) {
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

    private boolean indexSingleProduct(Long productId) {
        Product product = productRepository.findById(productId)
                .orElseThrow(() -> new RuntimeException("Product not found: " + productId));

        if (product.getImage() == null || product.getImage().isBlank()) {
            log.warn("Product {} has no image, skipping embedding", productId);
            return false;
        }

        if (embeddingRepository.existsByProductId(productId)) {
            markSuccess(productId);
            log.info("Product {} already has an embedding, skipping", productId);
            return false;
        }

        List<Float> embedding;
        try {
            embedding = clipEmbeddingService.getEmbeddingFromUrl(product.getImage());
        } catch (ClipImageFetchException e) {
            markFailure(productId, e);
            throw e;
        } catch (RuntimeException e) {
            markFailure(productId, new ClipImageFetchException(e.getMessage(), null, true, e));
            throw e;
        }

        String vectorString = ClipEmbeddingService.toVectorString(embedding);

        Boolean inserted = transactionTemplate.execute(status -> {
            if (embeddingRepository.existsByProductId(productId)) {
                return false;
            }

            embeddingRepository.insertEmbedding(
                    productId, vectorString, product.getImage(), "clip-vit-base-patch32");
            return true;
        });

        if (Boolean.TRUE.equals(inserted)) {
            markSuccess(productId);
            log.info("Indexed product {} with embedding", productId);
            return true;
        }

        markSuccess(productId);
        log.info("Product {} was indexed by another process, skipping", productId);
        return false;
    }

    private void markSuccess(Long productId) {
        transactionTemplate.executeWithoutResult(status -> {
            ProductEmbeddingStatus embeddingStatus = embeddingStatusRepository.findByProductId(productId)
                    .orElseGet(() -> createStatus(productId));
            embeddingStatus.setStatus(ProductEmbeddingStatus.Status.SUCCESS);
            embeddingStatus.setLastError(null);
            embeddingStatus.setLastHttpStatus(null);
            embeddingStatus.setLastSuccessAt(OffsetDateTime.now());
            embeddingStatus.setNextRetryAt(null);
            embeddingStatusRepository.save(embeddingStatus);
        });
    }

    private void markFailure(Long productId, ClipImageFetchException exception) {
        transactionTemplate.executeWithoutResult(status -> {
            ProductEmbeddingStatus embeddingStatus = embeddingStatusRepository.findByProductId(productId)
                    .orElseGet(() -> createStatus(productId));

            int attempts = embeddingStatus.getAttemptCount() == null ? 0 : embeddingStatus.getAttemptCount();
            attempts++;

            embeddingStatus.setAttemptCount(attempts);
            embeddingStatus.setLastAttemptAt(OffsetDateTime.now());
            embeddingStatus.setLastError(trimError(exception.getMessage()));
            embeddingStatus.setLastHttpStatus(exception.getHttpStatus());

            boolean shouldBlock = !exception.isRetryable() || attempts >= MAX_RETRYABLE_ATTEMPTS;
            if (shouldBlock) {
                embeddingStatus.setStatus(ProductEmbeddingStatus.Status.BLOCKED);
                embeddingStatus.setNextRetryAt(OffsetDateTime.now().plusHours(BLOCKED_STATUS_RETRY_DELAY_HOURS));
            } else {
                embeddingStatus.setStatus(ProductEmbeddingStatus.Status.RETRY);
                embeddingStatus.setNextRetryAt(OffsetDateTime.now().plusMinutes(backoffMinutes(attempts)));
            }

            embeddingStatusRepository.save(embeddingStatus);
        });
    }

    private ProductEmbeddingStatus createStatus(Long productId) {
        Product product = productRepository.getReferenceById(productId);
        ProductEmbeddingStatus status = new ProductEmbeddingStatus();
        status.setProduct(product);
        return status;
    }

    private int backoffMinutes(int attempts) {
        return switch (attempts) {
            case 1 -> 30;
            case 2 -> 120;
            case 3 -> 360;
            case 4 -> 720;
            default -> 1440;
        };
    }

    private String trimError(String message) {
        if (message == null) {
            return null;
        }
        return message.length() <= 1000 ? message : message.substring(0, 1000);
    }
}
