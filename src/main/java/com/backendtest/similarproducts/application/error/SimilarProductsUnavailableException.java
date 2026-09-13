package com.backendtest.similarproducts.application.error;

public final class SimilarProductsUnavailableException extends RuntimeException {

    public SimilarProductsUnavailableException(String message) {
        super(message);
    }

    public SimilarProductsUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
