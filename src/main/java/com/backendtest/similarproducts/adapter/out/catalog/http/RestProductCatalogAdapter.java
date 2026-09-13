package com.backendtest.similarproducts.adapter.out.catalog.http;

import java.net.SocketTimeoutException;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogException;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogFailure;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogPort;
import com.backendtest.similarproducts.config.ProductsRestClientConfig;
import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;
import tools.jackson.databind.JsonNode;

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
            JsonNode response = restClient.get()
                    .uri("/product/{productId}/similarids", productId.value())
                    .retrieve()
                    .body(JsonNode.class);
            return mapProductIds(response);
        } catch (RestClientResponseException exception) {
            throw mapStatusException(exception);
        } catch (ResourceAccessException exception) {
            throw mapResourceAccessException(exception);
        } catch (RestClientException | IllegalArgumentException exception) {
            throw invalidResponse(exception);
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
        } catch (RestClientResponseException exception) {
            throw mapStatusException(exception);
        } catch (ResourceAccessException exception) {
            throw mapResourceAccessException(exception);
        } catch (RestClientException | IllegalArgumentException exception) {
            throw invalidResponse(exception);
        }
    }

    private List<ProductId> mapProductIds(JsonNode response) {
        if (response == null || !response.isArray()) {
            throw new IllegalArgumentException("Similar product IDs response must be an array");
        }

        Set<ProductId> productIds = LinkedHashSet.newLinkedHashSet(response.size());
        for (JsonNode node : response) {
            if (!node.isString() && !node.isIntegralNumber()) {
                throw new IllegalArgumentException("Similar product ID must be a string or integer");
            }
            String value = node.isString() ? node.stringValue() : node.bigIntegerValue().toString();
            productIds.add(new ProductId(value));
        }
        return List.copyOf(productIds);
    }

    private ProductCatalogException mapStatusException(RestClientResponseException exception) {
        ProductCatalogFailure failure;
        if (exception.getStatusCode().value() == 404) {
            failure = ProductCatalogFailure.NOT_FOUND;
        } else if (exception.getStatusCode().is5xxServerError()) {
            failure = ProductCatalogFailure.SERVER_ERROR;
        } else {
            failure = ProductCatalogFailure.INVALID_RESPONSE;
        }
        return new ProductCatalogException(failure, "Product catalog returned an unexpected status", exception);
    }

    private ProductCatalogException invalidResponse(Exception exception) {
        return new ProductCatalogException(
                ProductCatalogFailure.INVALID_RESPONSE,
                "Product catalog returned an invalid response",
                exception);
    }

    private ProductCatalogException mapResourceAccessException(ResourceAccessException exception) {
        if (hasCause(exception, SocketTimeoutException.class)) {
            return new ProductCatalogException(
                    ProductCatalogFailure.TIMEOUT,
                    "Product catalog request timed out",
                    exception);
        }
        return new ProductCatalogException(
                ProductCatalogFailure.CONNECTION_ERROR,
                "Could not connect to the product catalog",
                exception);
    }

    private boolean hasCause(Throwable exception, Class<? extends Throwable> causeType) {
        Throwable cause = exception;
        while (cause != null) {
            if (causeType.isInstance(cause)) {
                return true;
            }
            cause = cause.getCause();
        }
        return false;
    }
}
