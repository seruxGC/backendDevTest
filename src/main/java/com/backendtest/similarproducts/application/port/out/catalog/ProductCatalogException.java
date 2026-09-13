package com.backendtest.similarproducts.application.port.out.catalog;

import java.util.Objects;

public final class ProductCatalogException extends RuntimeException {

    private final ProductCatalogFailure failure;

    public ProductCatalogException(ProductCatalogFailure failure, String message) {
        super(message);
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    public ProductCatalogException(ProductCatalogFailure failure, String message, Throwable cause) {
        super(message, cause);
        this.failure = Objects.requireNonNull(failure, "failure");
    }

    public ProductCatalogFailure failure() {
        return failure;
    }
}
