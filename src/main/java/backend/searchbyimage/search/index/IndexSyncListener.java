package backend.searchbyimage.search.index;

import backend.searchbyimage.config.SpringContext;
import backend.searchbyimage.domain.Category;
import backend.searchbyimage.domain.Product;
import backend.searchbyimage.domain.ProductDetail;
import backend.searchbyimage.domain.ProductSearchMeta;
import backend.searchbyimage.domain.Shop;
import backend.searchbyimage.service.ProductSearchIndexService;
import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * JPA entity listener that logs when indexed entities (Product, Shop) are created or updated.
 * Can be extended to trigger re-indexing of embeddings when product data changes.
 */
public class IndexSyncListener {

    private static final Logger log = LoggerFactory.getLogger(IndexSyncListener.class);

    @PostPersist
    public void onPostPersist(Object entity) {
        sync(entity);
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        sync(entity);
    }

    private void sync(Object entity) {
        log.debug("Entity changed, syncing search index: {}", entity.getClass().getSimpleName());
        ProductSearchIndexService searchIndexService = SpringContext.getBean(ProductSearchIndexService.class);

        if (entity instanceof Product product) {
            searchIndexService.syncProduct(product.getId());
            return;
        }
        if (entity instanceof ProductDetail detail && detail.getProduct() != null) {
            searchIndexService.syncProduct(detail.getProduct().getId());
            return;
        }
        if (entity instanceof ProductSearchMeta meta && meta.getProduct() != null) {
            searchIndexService.syncProduct(meta.getProduct().getId());
            return;
        }
        if (entity instanceof Shop shop) {
            searchIndexService.syncProductsByShop(shop.getId());
            return;
        }
        if (entity instanceof Category category) {
            searchIndexService.syncProductsByCategory(category.getId());
        }
    }
}
