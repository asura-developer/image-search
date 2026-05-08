package backend.searchbyimage.dto;

import lombok.Getter;

import java.util.List;

@Getter
public class ImageSearchResponse {

    private final boolean success = true;
    private final int code = 200;
    private final String message = "Successful";
    private final String error = null;
    private final Payload payload;

    public ImageSearchResponse(
            List<ProductSearchResult> data,
            int total,
            int currentPage,
            int perPage,
            int lastPage) {
        this.payload = new Payload(data, total, currentPage, perPage, lastPage);
    }

    @Getter
    public static class Payload {
        private final List<ProductSearchResult> data;
        private final int total;
        private final int currentPage;
        private final int perPage;
        private final int lastPage;

        public Payload(
                List<ProductSearchResult> data,
                int total,
                int currentPage,
                int perPage,
                int lastPage) {
            this.data = data;
            this.total = total;
            this.currentPage = currentPage;
            this.perPage = perPage;
            this.lastPage = lastPage;
        }
    }
}
