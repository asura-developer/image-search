package backend.searchbyimage.search.index;

import jakarta.persistence.PostPersist;
import jakarta.persistence.PostUpdate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class IndexSyncListener {

    private static final Logger log = LoggerFactory.getLogger(IndexSyncListener.class);

    @PostPersist
    public void onPostPersist(Object entity) {
        log.debug("Legacy JPA entity changed; UUID search index is maintained by explicit rebuild/sync endpoints: {}",
                entity.getClass().getSimpleName());
    }

    @PostUpdate
    public void onPostUpdate(Object entity) {
        log.debug("Legacy JPA entity changed; UUID search index is maintained by explicit rebuild/sync endpoints: {}",
                entity.getClass().getSimpleName());
    }
}
