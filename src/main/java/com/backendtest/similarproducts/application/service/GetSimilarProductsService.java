package com.backendtest.similarproducts.application.service;

import java.util.List;

import com.backendtest.similarproducts.application.error.SimilarProductsNotFoundException;
import com.backendtest.similarproducts.application.error.SimilarProductsUnavailableException;
import com.backendtest.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogException;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogPort;
import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.springframework.stereotype.Service;

@Service
public final class GetSimilarProductsService implements GetSimilarProductsUseCase {

    private final ProductCatalogPort productCatalog;
    private final ProductDetailsFetcher productDetailsFetcher;

    GetSimilarProductsService(
            ProductCatalogPort productCatalog,
            ProductDetailsFetcher productDetailsFetcher) {
        this.productCatalog = productCatalog;
        this.productDetailsFetcher = productDetailsFetcher;
    }

    @Override
    public List<Product> getSimilarProducts(ProductId productId) {
        List<ProductId> similarProductIds = getSimilarProductIds(productId);
        if (similarProductIds.isEmpty()) {
            return List.of();
        }

        return productDetailsFetcher.fetch(similarProductIds);
    }

    private List<ProductId> getSimilarProductIds(ProductId productId) {
        try {
            return List.copyOf(productCatalog.getSimilarProductIds(productId));
        } catch (ProductCatalogException exception) {
            throw switch (exception.failure()) {
                case NOT_FOUND -> new SimilarProductsNotFoundException(
                        "Product was not found: " + productId.value(), exception);
                case UNAVAILABLE -> new SimilarProductsUnavailableException(
                        "Could not retrieve similar product IDs", exception);
            };
        }
    }

}
