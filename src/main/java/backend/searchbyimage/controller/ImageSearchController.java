package backend.searchbyimage.controller;

import backend.searchbyimage.dto.ImageSearchResponse;
import backend.searchbyimage.service.ImageSearchService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/search")
public class ImageSearchController {

    private final ImageSearchService imageSearchService;

    public ImageSearchController(ImageSearchService imageSearchService) {
        this.imageSearchService = imageSearchService;
    }

    /**
     * POST /api/v1/search/image
     * Upload an image file to find similar products.
     */
    @PostMapping(value = "/image", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImageSearchResponse> searchByImage(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "perPage", required = false) Integer perPage,
            @RequestParam(value = "threshold", required = false) Double threshold) throws IOException {

        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }

        ImageSearchResponse response = imageSearchService.searchByImage(file, page, perPage, limit, threshold);
        return ResponseEntity.ok(response);
    }

    /**
     * POST /api/v1/search/image-url
     * Provide an image URL to find similar products.
     */
    @PostMapping("/image-url")
    public ResponseEntity<ImageSearchResponse> searchByImageUrl(
            @RequestBody Map<String, String> body,
            @RequestParam(value = "limit", required = false) Integer limit,
            @RequestParam(value = "page", required = false) Integer page,
            @RequestParam(value = "perPage", required = false) Integer perPage,
            @RequestParam(value = "threshold", required = false) Double threshold) {

        String imageUrl = body.get("image_url");
        if (imageUrl == null || imageUrl.isBlank()) {
            return ResponseEntity.badRequest().build();
        }

        ImageSearchResponse response = imageSearchService.searchByImageUrl(imageUrl, page, perPage, limit, threshold);
        return ResponseEntity.ok(response);
    }
}
