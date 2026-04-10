package backend.searchbyimage.service;

import lombok.Getter;

@Getter
public class ClipImageFetchException extends RuntimeException {

    private final Integer httpStatus;
    private final boolean retryable;

    public ClipImageFetchException(String message, Integer httpStatus, boolean retryable, Throwable cause) {
        super(message, cause);
        this.httpStatus = httpStatus;
        this.retryable = retryable;
    }

}
