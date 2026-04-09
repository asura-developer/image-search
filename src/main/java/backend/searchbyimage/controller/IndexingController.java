package backend.searchbyimage.controller;

import backend.searchbyimage.service.ImageSearchService;
import backend.searchbyimage.service.ProductSearchIndexService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/index")
public class IndexingController {

    private final ImageSearchService imageSearchService;
    private final ProductSearchIndexService productSearchIndexService;

    public IndexingController(
            ImageSearchService imageSearchService,
            ProductSearchIndexService productSearchIndexService) {
        this.imageSearchService = imageSearchService;
        this.productSearchIndexService = productSearchIndexService;
    }

    /**
     * POST /api/v1/index/product/{id}
     * Index a single product (generate and store its image embedding).
     */
    @PostMapping("/product/{id}")
    public ResponseEntity<Map<String, String>> indexProduct(@PathVariable Long id) {
        boolean indexed = imageSearchService.indexProduct(id);
        return ResponseEntity.ok(Map.of(
                "status", indexed ? "indexed" : "skipped",
                "productId", id.toString()
        ));
    }

    /**
     * POST /api/v1/index/batch?size=100
     * Batch index products that don't have embeddings yet.
     */
    @PostMapping("/batch")
    public ResponseEntity<Map<String, Object>> indexBatch(
            @RequestParam(value = "size", defaultValue = "100") int size) {
        int indexed = imageSearchService.indexBatch(size);
        return ResponseEntity.ok(Map.of("status", "completed", "indexed", indexed));
    }

    /**
     * POST /api/v1/index/all?batchSize=100
     * Index ALL products that don't have embeddings yet.
     */
    @PostMapping("/all")
    public ResponseEntity<Map<String, Object>> indexAll(
            @RequestParam(value = "batchSize", defaultValue = "100") int batchSize) {
        int indexed = imageSearchService.indexAll(batchSize);
        return ResponseEntity.ok(Map.of("status", "completed", "totalIndexed", indexed));
    }

    @PostMapping("/search/rebuild")
    public ResponseEntity<Map<String, Object>> rebuildSearchIndex() {
        int indexed = productSearchIndexService.rebuildAll();
        return ResponseEntity.ok(Map.of("status", "completed", "indexed", indexed));
    }

    @PostMapping("/search/product/{id}")
    public ResponseEntity<Map<String, Object>> rebuildSearchDocument(@PathVariable Long id) {
        boolean indexed = productSearchIndexService.syncProduct(id);
        return ResponseEntity.ok(Map.of("status", indexed ? "indexed" : "missing", "productId", id));
    }
}
