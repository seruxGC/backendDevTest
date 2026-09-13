package com.backendtest.similarproducts.application.error;

public final class SimilarProductsTimeoutException extends RuntimeException {

    public SimilarProductsTimeoutException(String message) {
        super(message);
    }

    public SimilarProductsTimeoutException(String message, Throwable cause) {
        super(message, cause);
    }
}
