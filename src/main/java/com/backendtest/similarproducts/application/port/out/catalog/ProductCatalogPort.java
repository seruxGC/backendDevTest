package com.backendtest.similarproducts.application.port.out.catalog;

import java.util.List;

import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;

public interface ProductCatalogPort {

    List<ProductId> getSimilarProductIds(ProductId productId);

    Product getProduct(ProductId productId);
}
