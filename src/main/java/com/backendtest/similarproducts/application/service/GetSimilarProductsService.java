package com.backendtest.similarproducts.application.service;

import java.time.Duration;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
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
import com.backendtest.similarproducts.application.port.out.telemetry.DetailFailureReason;
import com.backendtest.similarproducts.application.port.out.telemetry.SimilarProductsTelemetry;
import com.backendtest.similarproducts.application.port.out.telemetry.SimilarProductsTelemetryEvent;
import com.backendtest.similarproducts.application.port.out.telemetry.SimilarProductsTelemetryEvent.Outcome;
import com.backendtest.similarproducts.config.ProductDetailsExecutorConfig;
import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public final class GetSimilarProductsService implements GetSimilarProductsUseCase {

    private final ProductCatalogPort productCatalog;
    private final SimilarProductsTelemetry telemetry;
    private final Executor detailExecutor;
    private final int maxConcurrencyPerRequest;

    public GetSimilarProductsService(
            ProductCatalogPort productCatalog,
            SimilarProductsTelemetry telemetry,
            @Qualifier(ProductDetailsExecutorConfig.PRODUCT_DETAILS_EXECUTOR) Executor detailExecutor,
            @Value("${products.client.max-concurrency-per-request}") int maxConcurrencyPerRequest) {
        this.productCatalog = Objects.requireNonNull(productCatalog, "productCatalog");
        this.telemetry = Objects.requireNonNull(telemetry, "telemetry");
        this.detailExecutor = Objects.requireNonNull(detailExecutor, "detailExecutor");
        if (maxConcurrencyPerRequest < 1) {
            throw new IllegalArgumentException("Maximum concurrency per request must be positive");
        }
        this.maxConcurrencyPerRequest = maxConcurrencyPerRequest;
    }

    @Override
    public List<Product> getSimilarProducts(ProductId productId) {
        Objects.requireNonNull(productId, "productId");
        long startedAt = System.nanoTime();
        int requestedDetailCount = 0;
        DetailSummary summary = DetailSummary.empty();
        boolean businessOperationCompleted = false;

        try {
            List<ProductId> similarProductIds = getSimilarProductIds(productId);
            requestedDetailCount = similarProductIds.size();
            if (similarProductIds.isEmpty()) {
                businessOperationCompleted = true;
                recordTelemetry(Outcome.COMPLETE, startedAt, 0, summary);
                return List.of();
            }

            DetailResult[] results = fetchDetailsConcurrently(similarProductIds);
            summary = summarize(results);
            List<Product> products = resolveResult(summary);
            Outcome outcome = summary.omissions().isEmpty() ? Outcome.COMPLETE : Outcome.PARTIAL;
            businessOperationCompleted = true;
            recordTelemetry(outcome, startedAt, requestedDetailCount, summary);
            return products;
        } catch (RuntimeException exception) {
            if (!businessOperationCompleted) {
                recordTelemetry(Outcome.FAILED, startedAt, requestedDetailCount, summary);
            }
            throw exception;
        }
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

    private DetailSummary summarize(DetailResult[] results) {
        List<Product> products = new ArrayList<>(results.length);
        EnumMap<DetailFailureReason, Integer> omissions = new EnumMap<>(DetailFailureReason.class);

        for (DetailResult result : results) {
            switch (result) {
                case DetailSuccess(Product product) -> products.add(product);
                case DetailFailure(ProductCatalogFailure reason) ->
                        omissions.merge(toDetailFailureReason(reason), 1, Integer::sum);
            }
        }
        return new DetailSummary(List.copyOf(products), Map.copyOf(omissions));
    }

    private List<Product> resolveResult(DetailSummary summary) {
        boolean hasTechnicalFailure = false;
        boolean allTechnicalFailuresAreTimeouts = true;

        for (Map.Entry<DetailFailureReason, Integer> omission : summary.omissions().entrySet()) {
            if (omission.getKey() == DetailFailureReason.NOT_FOUND) {
                continue;
            }
            hasTechnicalFailure = true;
            if (omission.getKey() != DetailFailureReason.TIMEOUT) {
                allTechnicalFailuresAreTimeouts = false;
            }
        }

        if (!summary.products().isEmpty()) {
            return summary.products();
        }
        if (!hasTechnicalFailure) {
            return List.of();
        }
        if (allTechnicalFailuresAreTimeouts) {
            throw new SimilarProductsTimeoutException("All product detail requests timed out");
        }
        throw new SimilarProductsUnavailableException("No product detail could be retrieved");
    }

    private DetailFailureReason toDetailFailureReason(ProductCatalogFailure failure) {
        return switch (failure) {
            case NOT_FOUND -> DetailFailureReason.NOT_FOUND;
            case TIMEOUT -> DetailFailureReason.TIMEOUT;
            case CONNECTION_ERROR -> DetailFailureReason.CONNECTION_ERROR;
            case SERVER_ERROR, INVALID_RESPONSE -> DetailFailureReason.SERVER_ERROR;
        };
    }

    private void recordTelemetry(
            Outcome outcome,
            long startedAt,
            int requestedDetailCount,
            DetailSummary summary) {
        telemetry.record(new SimilarProductsTelemetryEvent(
                outcome,
                Duration.ofNanos(System.nanoTime() - startedAt),
                requestedDetailCount,
                summary.products().size(),
                summary.omissions()));
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

    private record DetailSummary(
            List<Product> products,
            Map<DetailFailureReason, Integer> omissions) {

        private static DetailSummary empty() {
            return new DetailSummary(List.of(), Map.of());
        }
    }
}
