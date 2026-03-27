package backend.searchbyimage.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
@AllArgsConstructor
public class ImageSearchResponse {

    private int totalResults;
    private List<ProductSearchResult> results;
}
