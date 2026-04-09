package backend.searchbyimage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class TextEmbeddingRequest {

    private String text;
}
