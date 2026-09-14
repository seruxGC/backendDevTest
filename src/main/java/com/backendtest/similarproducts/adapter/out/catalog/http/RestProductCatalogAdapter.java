package com.backendtest.similarproducts.adapter.out.catalog.http;

import java.util.Arrays;
import java.util.List;

import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogException;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogFailure;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogPort;
import com.backendtest.similarproducts.config.ProductsRestClientConfig;
import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

@Component
final class RestProductCatalogAdapter implements ProductCatalogPort {

    private final RestClient restClient;

    RestProductCatalogAdapter(
            @Qualifier(ProductsRestClientConfig.PRODUCTS_REST_CLIENT) RestClient restClient) {
        this.restClient = restClient;
    }

    @Override
    public List<ProductId> getSimilarProductIds(ProductId productId) {
        try {
            String[] response = restClient.get()
                    .uri("/product/{productId}/similarids", productId.value())
                    .retrieve()
                    .body(String[].class);
            if (response == null) {
                throw new IllegalArgumentException("Similar product IDs response body must not be null");
            }
            return Arrays.stream(response)
                    .map(ProductId::new)
                    .distinct()
                    .toList();
        } catch (RestClientException | IllegalArgumentException exception) {
            throw mapFailure(exception);
        }
    }

    @Override
    public Product getProduct(ProductId productId) {
        try {
            ExternalProductResponse response = restClient.get()
                    .uri("/product/{productId}", productId.value())
                    .retrieve()
                    .body(ExternalProductResponse.class);
            if (response == null) {
                throw new IllegalArgumentException("Product response body must not be null");
            }
            return response.toDomain();
        } catch (RestClientException | IllegalArgumentException exception) {
            throw mapFailure(exception);
        }
    }

    private ProductCatalogException mapFailure(Exception exception) {
        if (exception instanceof RestClientResponseException responseException
                && responseException.getStatusCode().value() == 404) {
            return new ProductCatalogException(
                    ProductCatalogFailure.NOT_FOUND,
                    "Product catalog returned an unexpected status",
                    exception);
        }

        return new ProductCatalogException(
                ProductCatalogFailure.UNAVAILABLE,
                "Product catalog is unavailable",
                exception);
    }
}
