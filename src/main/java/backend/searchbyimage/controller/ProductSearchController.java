package backend.searchbyimage.controller;

import backend.searchbyimage.dto.ProductTextSearchResponse;
import backend.searchbyimage.service.ProductSearchService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/search")
public class ProductSearchController {

    private final ProductSearchService productSearchService;

    public ProductSearchController(ProductSearchService productSearchService) {
        this.productSearchService = productSearchService;
    }

    @GetMapping("/products")
    public ResponseEntity<ProductTextSearchResponse> searchProducts(
            @RequestParam("q") String query,
            @RequestParam(value = "platform", required = false) String platform,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "shop", required = false) String shop,
            @RequestParam(value = "minPrice", required = false) Double minPrice,
            @RequestParam(value = "maxPrice", required = false) Double maxPrice,
            @RequestParam(value = "sort", required = false) String sort,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "size", required = false) Integer size) {
        return ResponseEntity.ok(productSearchService.searchProducts(
                query, platform, category, shop, minPrice, maxPrice, sort, page, size));
    }

    @GetMapping("/suggestions")
    public ResponseEntity<Map<String, List<String>>> suggestions(
            @RequestParam("q") String query,
            @RequestParam(value = "limit", required = false) Integer limit) {
        return ResponseEntity.ok(Map.of("suggestions", productSearchService.suggestions(query, limit)));
    }
}
