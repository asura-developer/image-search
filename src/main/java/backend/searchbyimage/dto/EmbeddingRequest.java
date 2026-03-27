package backend.searchbyimage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class EmbeddingRequest {

    @JsonProperty("image_url")
    private String imageUrl;
}
