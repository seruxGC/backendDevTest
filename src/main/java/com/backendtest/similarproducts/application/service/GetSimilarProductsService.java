package com.backendtest.similarproducts.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import com.backendtest.similarproducts.application.error.SimilarProductsNotFoundException;
import com.backendtest.similarproducts.application.error.SimilarProductsTimeoutException;
import com.backendtest.similarproducts.application.error.SimilarProductsUnavailableException;
import com.backendtest.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogException;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogFailure;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogPort;
import com.backendtest.similarproducts.config.ProductDetailsExecutorConfig;
import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class GetSimilarProductsService implements GetSimilarProductsUseCase {

    private final ProductCatalogPort productCatalog;
    private final Executor detailExecutor;
    private final int maxConcurrencyPerRequest;

    public GetSimilarProductsService(
            ProductCatalogPort productCatalog,
            @Qualifier(ProductDetailsExecutorConfig.PRODUCT_DETAILS_EXECUTOR) Executor detailExecutor,
            @Value("${products.client.max-concurrency-per-request}") int maxConcurrencyPerRequest) {
        this.productCatalog = Objects.requireNonNull(productCatalog, "productCatalog");
        this.detailExecutor = Objects.requireNonNull(detailExecutor, "detailExecutor");
        if (maxConcurrencyPerRequest < 1) {
            throw new IllegalArgumentException("Maximum concurrency per request must be positive");
        }
        this.maxConcurrencyPerRequest = maxConcurrencyPerRequest;
    }

    @Override
    public List<Product> getSimilarProducts(ProductId productId) {
        Objects.requireNonNull(productId, "productId");

        List<ProductId> similarProductIds = getSimilarProductIds(productId);
        if (similarProductIds.isEmpty()) {
            return List.of();
        }

        DetailResult[] results = fetchDetailsConcurrently(similarProductIds);
        return resolveResult(results);
    }

    private List<ProductId> getSimilarProductIds(ProductId productId) {
        try {
            return List.copyOf(productCatalog.getSimilarProductIds(productId));
        } catch (ProductCatalogException exception) {
            throw switch (exception.failure()) {
                case NOT_FOUND -> new SimilarProductsNotFoundException(
                        "Product was not found: " + productId.value(), exception);
                case TIMEOUT -> new SimilarProductsTimeoutException(
                        "Timed out while retrieving similar product IDs", exception);
                case SERVER_ERROR, CONNECTION_ERROR, INVALID_RESPONSE ->
                        new SimilarProductsUnavailableException(
                                "Could not retrieve similar product IDs", exception);
            };
        }
    }

    private DetailResult[] fetchDetailsConcurrently(List<ProductId> productIds) {
        DetailResult[] results = new DetailResult[productIds.size()];
        AtomicInteger nextIndex = new AtomicInteger();
        int workerCount = Math.min(productIds.size(), maxConcurrencyPerRequest);
        List<CompletableFuture<Void>> workers = new ArrayList<>(workerCount);

        for (int worker = 0; worker < workerCount; worker++) {
            workers.add(CompletableFuture.runAsync(
                    () -> runWorker(productIds, results, nextIndex), detailExecutor));
        }

        awaitWorkers(workers);
        return results;
    }

    private void runWorker(
            List<ProductId> productIds,
            DetailResult[] results,
            AtomicInteger nextIndex) {
        int index;
        while ((index = nextIndex.getAndIncrement()) < productIds.size()) {
            results[index] = fetchDetail(productIds.get(index));
        }
    }

    private DetailResult fetchDetail(ProductId productId) {
        try {
            return new DetailSuccess(productCatalog.getProduct(productId));
        } catch (ProductCatalogException exception) {
            return new DetailFailure(exception.failure());
        }
    }

    private void awaitWorkers(List<CompletableFuture<Void>> workers) {
        try {
            CompletableFuture.allOf(workers.toArray(CompletableFuture[]::new)).join();
        } catch (CompletionException exception) {
            if (exception.getCause() instanceof RuntimeException cause) {
                throw cause;
            }
            if (exception.getCause() instanceof Error cause) {
                throw cause;
            }
            throw exception;
        }
    }

    private List<Product> resolveResult(DetailResult[] results) {
        List<Product> products = new ArrayList<>(results.length);
        boolean hasTechnicalFailure = false;
        boolean allTechnicalFailuresAreTimeouts = true;

        for (DetailResult result : results) {
            switch (result) {
                case DetailSuccess(Product product) -> products.add(product);
                case DetailFailure(ProductCatalogFailure reason) -> {
                    if (reason == ProductCatalogFailure.NOT_FOUND) {
                        continue;
                    }
                    hasTechnicalFailure = true;
                    if (reason != ProductCatalogFailure.TIMEOUT) {
                        allTechnicalFailuresAreTimeouts = false;
                    }
                }
            }
        }

        if (!products.isEmpty()) {
            return List.copyOf(products);
        }
        if (!hasTechnicalFailure) {
            return List.of();
        }
        if (allTechnicalFailuresAreTimeouts) {
            throw new SimilarProductsTimeoutException("All product detail requests timed out");
        }
        throw new SimilarProductsUnavailableException("No product detail could be retrieved");
    }

    private sealed interface DetailResult permits DetailSuccess, DetailFailure {
    }

    private record DetailSuccess(Product product) implements DetailResult {

        private DetailSuccess {
            Objects.requireNonNull(product, "product");
        }
    }

    private record DetailFailure(ProductCatalogFailure reason) implements DetailResult {

        private DetailFailure {
            Objects.requireNonNull(reason, "reason");
        }
    }
}
