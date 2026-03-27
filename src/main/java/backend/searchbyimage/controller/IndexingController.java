package backend.searchbyimage.controller;

import backend.searchbyimage.service.ImageSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/index")
public class IndexingController {

    private final ImageSearchService imageSearchService;

    public IndexingController(ImageSearchService imageSearchService) {
        this.imageSearchService = imageSearchService;
    }

    /**
     * POST /api/v1/index/product/{id}
     * Index a single product (generate and store its image embedding).
     */
    @PostMapping("/product/{id}")
    public ResponseEntity<Map<String, String>> indexProduct(@PathVariable Long id) {
        imageSearchService.indexProduct(id);
        return ResponseEntity.ok(Map.of("status", "indexed", "productId", id.toString()));
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
}
