package com.backendtest.similarproducts.adapter.in.web;

import java.util.List;

import com.backendtest.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/product")
public final class SimilarProductsController {

    private final GetSimilarProductsUseCase getSimilarProducts;

    public SimilarProductsController(GetSimilarProductsUseCase getSimilarProducts) {
        this.getSimilarProducts = getSimilarProducts;
    }

    @GetMapping("/{productId}/similar")
    public List<ProductResponse> getSimilarProducts(@PathVariable String productId) {
        return getSimilarProducts.getSimilarProducts(new ProductId(productId)).stream()
                .map(ProductResponse::from)
                .toList();
    }
}
