package com.backendtest.similarproducts.domain.model;

import java.math.BigDecimal;

public record Product(ProductId id, String name, BigDecimal price, boolean availability) {

    public Product {
        if (id == null) {
            throw new IllegalArgumentException("Product ID must not be null");
        }
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Product name must not be blank");
        }
        if (price == null) {
            throw new IllegalArgumentException("Product price must not be null");
        }
    }
}
