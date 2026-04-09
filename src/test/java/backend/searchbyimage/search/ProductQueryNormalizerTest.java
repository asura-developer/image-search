package backend.searchbyimage.search;

import backend.searchbyimage.search.model.ProductSearchFilter;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertIterableEquals;

class ProductQueryNormalizerTest {

    private final ProductQueryNormalizer normalizer = new ProductQueryNormalizer();

    @Test
    void normalizesForSearchConsistency() {
        var filter = new ProductSearchFilter(null, null, null, null, null, null, 1, 200);
        var query = normalizer.normalize("  Caf\u00e9 Phone Case!!  ", filter, 20, 60);

        assertEquals("cafe phone case", query.normalized());
        assertEquals("cafe:* & phone:* & case:*", query.tsQuery());
        assertEquals("cafe <-> phone <-> case", query.phraseQuery());
        assertIterableEquals(java.util.List.of("cafe", "phone", "case"), query.tokens());
        assertEquals(1, query.page());
        assertEquals(60, query.size());
    }
}
