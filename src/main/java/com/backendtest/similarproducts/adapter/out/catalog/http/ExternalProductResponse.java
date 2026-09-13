package com.backendtest.similarproducts.adapter.out.catalog.http;

import java.math.BigDecimal;

import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;

record ExternalProductResponse(
        String id,
        String name,
        BigDecimal price,
        Boolean availability) {

    Product toDomain() {
        if (availability == null) {
            throw new IllegalArgumentException("Product availability must not be null");
        }
        return new Product(new ProductId(id), name, price, availability);
    }
}
