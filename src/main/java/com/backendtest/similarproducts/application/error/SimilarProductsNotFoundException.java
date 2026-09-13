package com.backendtest.similarproducts.application.error;

public final class SimilarProductsNotFoundException extends RuntimeException {

    public SimilarProductsNotFoundException(String message, Throwable cause) {
        super(message, cause);
    }
}
