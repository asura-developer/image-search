package backend.searchbyimage.service;

import backend.searchbyimage.dto.EmbeddingRequest;
import backend.searchbyimage.dto.EmbeddingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Client for the Python CLIP embedding microservice.
 * Sends images (file upload or URL) and receives 512-dim embedding vectors.
 */
@Service
public class ClipEmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(ClipEmbeddingService.class);

    private final RestTemplate restTemplate;
    private final String clipServiceUrl;

    public ClipEmbeddingService(
            RestTemplate restTemplate,
            @Value("${clip.service.url}") String clipServiceUrl) {
        this.restTemplate = restTemplate;
        this.clipServiceUrl = clipServiceUrl;
    }

    /**
     * Get embedding from an uploaded image file.
     */
    public List<Float> getEmbeddingFromFile(MultipartFile file) throws IOException {
        String url = clipServiceUrl + "/embed/image";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename();
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<EmbeddingResponse> response = restTemplate.exchange(
                url, HttpMethod.POST, requestEntity, EmbeddingResponse.class);

        if (response.getBody() == null || response.getBody().getEmbedding() == null) {
            throw new RuntimeException("Empty embedding response from CLIP service");
        }

        return response.getBody().getEmbedding();
    }

    /**
     * Get embedding from an image URL.
     */
    public List<Float> getEmbeddingFromUrl(String imageUrl) {
        String url = clipServiceUrl + "/embed/url";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        EmbeddingRequest request = new EmbeddingRequest(imageUrl);
        HttpEntity<EmbeddingRequest> requestEntity = new HttpEntity<>(request, headers);

        ResponseEntity<EmbeddingResponse> response;
        try {
            response = restTemplate.exchange(
                    url, HttpMethod.POST, requestEntity, EmbeddingResponse.class);
        } catch (HttpStatusCodeException e) {
            String responseBody = e.getResponseBodyAsString();
            log.warn("CLIP service rejected image URL {} with status {}: {}",
                    imageUrl, e.getStatusCode(), responseBody);
            throw new ClipImageFetchException(
                    "CLIP service failed to fetch image URL: " + imageUrl,
                    e.getStatusCode().value(),
                    isRetryableStatus(e.getStatusCode().value()),
                    e
            );
        } catch (RuntimeException e) {
            throw new ClipImageFetchException(
                    "CLIP service request failed for image URL: " + imageUrl,
                    null,
                    true,
                    e
            );
        }

        if (response.getBody() == null || response.getBody().getEmbedding() == null) {
            throw new RuntimeException("Empty embedding response from CLIP service");
        }

        return response.getBody().getEmbedding();
    }

    private boolean isRetryableStatus(int statusCode) {
        return statusCode == 408 || statusCode == 420 || statusCode == 425 || statusCode == 429
                || statusCode == 500 || statusCode == 502 || statusCode == 503 || statusCode == 504;
    }

    /**
     * Convert a float list to pgvector string format: [0.1,0.2,0.3,...]
     */
    public static String toVectorString(List<Float> embedding) {
        return "[" + embedding.stream()
                .map(String::valueOf)
                .collect(Collectors.joining(",")) + "]";
    }
}
