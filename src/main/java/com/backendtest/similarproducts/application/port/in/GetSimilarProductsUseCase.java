package com.backendtest.similarproducts.application.port.in;

import java.util.List;

import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;

public interface GetSimilarProductsUseCase {

    List<Product> getSimilarProducts(ProductId productId);
}
