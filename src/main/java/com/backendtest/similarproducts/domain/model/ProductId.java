package com.backendtest.similarproducts.domain.model;

public record ProductId(String value) {

    public ProductId {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Product ID must not be blank");
        }
    }
}
