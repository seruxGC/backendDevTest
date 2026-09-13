package com.backendtest.similarproducts.adapter.in.web;

import java.math.BigDecimal;

import com.backendtest.similarproducts.domain.model.Product;

public record ProductResponse(
        String id,
        String name,
        BigDecimal price,
        boolean availability) {

    static ProductResponse from(Product product) {
        return new ProductResponse(
                product.id().value(),
                product.name(),
                product.price(),
                product.availability());
    }
}
