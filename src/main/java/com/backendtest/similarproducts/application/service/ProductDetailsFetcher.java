package com.backendtest.similarproducts.application.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;

import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogException;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogPort;
import com.backendtest.similarproducts.config.ProductDetailsExecutorConfig;
import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
final class ProductDetailsFetcher {

    private final ProductCatalogPort productCatalog;
    private final Executor detailExecutor;
    private final int maxConcurrencyPerRequest;

    ProductDetailsFetcher(
            ProductCatalogPort productCatalog,
            @Qualifier(ProductDetailsExecutorConfig.PRODUCT_DETAILS_EXECUTOR) Executor detailExecutor,
            @Value("${products.client.max-concurrency-per-request}") int maxConcurrencyPerRequest) {
        this.productCatalog = productCatalog;
        this.detailExecutor = detailExecutor;
        if (maxConcurrencyPerRequest < 1) {
            throw new IllegalArgumentException("Maximum concurrency per request must be positive");
        }
        this.maxConcurrencyPerRequest = maxConcurrencyPerRequest;
    }

    List<Product> fetch(List<ProductId> productIds) {
        DetailResult[] detailResults = fetchDetailResults(productIds);
        return collectSuccessfulProducts(detailResults);
    }

    private DetailResult[] fetchDetailResults(List<ProductId> productIds) {
        DetailResult[] detailResults = new DetailResult[productIds.size()];
        AtomicInteger nextProductIndex = new AtomicInteger();
        int workerCount = Math.min(productIds.size(), maxConcurrencyPerRequest);
        CompletableFuture<?>[] workers = new CompletableFuture<?>[workerCount];

        for (int workerIndex = 0; workerIndex < workerCount; workerIndex++) {
            workers[workerIndex] = CompletableFuture.runAsync(
                    () -> runWorker(productIds, detailResults, nextProductIndex), detailExecutor);
        }

        CompletableFuture.allOf(workers).join();
        return detailResults;
    }

    private void runWorker(
            List<ProductId> productIds,
            DetailResult[] detailResults,
            AtomicInteger nextProductIndex) {
        int index;
        while ((index = nextProductIndex.getAndIncrement()) < productIds.size()) {
            detailResults[index] = fetchDetail(productIds.get(index));
        }
    }

    private DetailResult fetchDetail(ProductId productId) {
        try {
            Product product = productCatalog.getProduct(productId);
            return new DetailSuccess(product);
        } catch (ProductCatalogException ignored) {
            return OmittedDetail.INSTANCE;
        }
    }

    private List<Product> collectSuccessfulProducts(DetailResult[] detailResults) {
        List<Product> products = new ArrayList<>(detailResults.length);

        for (DetailResult detailResult : detailResults) {
            if (detailResult instanceof DetailSuccess(Product product)) {
                products.add(product);
            }
        }

        return List.copyOf(products);
    }

    private sealed interface DetailResult permits DetailSuccess, OmittedDetail {
    }

    private record DetailSuccess(Product product) implements DetailResult {
    }

    private enum OmittedDetail implements DetailResult {
        INSTANCE
    }
}
