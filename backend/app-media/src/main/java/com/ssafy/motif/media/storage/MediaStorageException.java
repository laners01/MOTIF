package com.ssafy.motif.media.storage;

/** S3 입출력 실패. */
public class MediaStorageException extends RuntimeException {

    public MediaStorageException(String message, Throwable cause) {
        super(message, cause);
    }
}
