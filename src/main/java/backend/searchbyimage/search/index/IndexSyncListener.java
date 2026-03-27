package backend.searchbyimage.search.index;

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
        log.debug("Entity persisted, may need re-indexing: {}", entity.getClass().getSimpleName());
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        log.debug("Entity updated, may need re-indexing: {}", entity.getClass().getSimpleName());
    }
}
