package com.backendtest.similarproducts.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogPort;
import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;

@Timeout(5)
class GetSimilarProductsServiceConcurrencyTest {

    private static final ProductId REQUESTED_ID = new ProductId("requested");

    @Test
    void boundsWorkersFuturesAndConcurrentCalls() throws Exception {
        List<ProductId> ids = productIds(6);
        CountDownLatch firstWorkersStarted = new CountDownLatch(2);
        CountDownLatch releaseCalls = new CountDownLatch(1);
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger maximumActiveCalls = new AtomicInteger();

        ProductCatalogPort catalog = catalog(ids, id -> {
            int active = activeCalls.incrementAndGet();
            maximumActiveCalls.accumulateAndGet(active, Math::max);
            firstWorkersStarted.countDown();
            await(releaseCalls);
            activeCalls.decrementAndGet();
            return product(id);
        });

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
                ExecutorService caller = Executors.newSingleThreadExecutor()) {
            CountingExecutor countingExecutor = new CountingExecutor(virtualExecutor);
            GetSimilarProductsService service = new GetSimilarProductsService(catalog, countingExecutor, 2);

            CompletableFuture<List<Product>> result = CompletableFuture.supplyAsync(
                    () -> service.getSimilarProducts(REQUESTED_ID), caller);

            assertThat(firstWorkersStarted.await(2, TimeUnit.SECONDS)).isTrue();
            assertThat(countingExecutor.submissions()).isEqualTo(2);
            assertThat(activeCalls).hasValue(2);

            releaseCalls.countDown();

            assertThat(result.get(2, TimeUnit.SECONDS)).extracting(Product::id).containsExactlyElementsOf(ids);
            assertThat(maximumActiveCalls).hasValue(2);
        }
    }

    @Test
    void preservesInputOrderWhenDetailsFinishOutOfOrder() throws Exception {
        List<ProductId> ids = productIds(3);
        CountDownLatch allStarted = new CountDownLatch(3);
        Map<ProductId, CountDownLatch> releases = new ConcurrentHashMap<>();
        Map<ProductId, CountDownLatch> completions = new ConcurrentHashMap<>();
        ids.forEach(id -> {
            releases.put(id, new CountDownLatch(1));
            completions.put(id, new CountDownLatch(1));
        });

        ProductCatalogPort catalog = catalog(ids, id -> {
            allStarted.countDown();
            await(releases.get(id));
            completions.get(id).countDown();
            return product(id);
        });

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
                ExecutorService caller = Executors.newSingleThreadExecutor()) {
            GetSimilarProductsService service = new GetSimilarProductsService(catalog, virtualExecutor, 3);
            CompletableFuture<List<Product>> result = CompletableFuture.supplyAsync(
                    () -> service.getSimilarProducts(REQUESTED_ID), caller);

            assertThat(allStarted.await(2, TimeUnit.SECONDS)).isTrue();
            releaseAndAwait(ids.get(2), releases, completions);
            releaseAndAwait(ids.get(1), releases, completions);
            releaseAndAwait(ids.get(0), releases, completions);

            List<Product> products = result.get(2, TimeUnit.SECONDS);
            assertThat(products).extracting(Product::id).containsExactlyElementsOf(ids);
            assertThatThrownBy(() -> products.add(product(new ProductId("4"))))
                    .isInstanceOf(UnsupportedOperationException.class);
        }
    }

    private ProductCatalogPort catalog(List<ProductId> ids, Function<ProductId, Product> fetchProduct) {
        return new ProductCatalogPort() {
            @Override
            public List<ProductId> getSimilarProductIds(ProductId productId) {
                return ids;
            }

            @Override
            public Product getProduct(ProductId productId) {
                return fetchProduct.apply(productId);
            }
        };
    }

    private List<ProductId> productIds(int count) {
        return java.util.stream.IntStream.rangeClosed(1, count)
                .mapToObj(Integer::toString)
                .map(ProductId::new)
                .toList();
    }

    private Product product(ProductId id) {
        return new Product(id, "Product " + id.value(), BigDecimal.ONE, true);
    }

    private void releaseAndAwait(
            ProductId id,
            Map<ProductId, CountDownLatch> releases,
            Map<ProductId, CountDownLatch> completions) throws InterruptedException {
        releases.get(id).countDown();
        assertThat(completions.get(id).await(2, TimeUnit.SECONDS)).isTrue();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(2, TimeUnit.SECONDS)) {
                throw new AssertionError("Timed out waiting for test coordination");
            }
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new AssertionError("Test coordination was interrupted", exception);
        }
    }

    private static final class CountingExecutor implements Executor {

        private final Executor delegate;
        private final AtomicInteger submissions = new AtomicInteger();

        private CountingExecutor(Executor delegate) {
            this.delegate = delegate;
        }

        @Override
        public void execute(Runnable command) {
            submissions.incrementAndGet();
            delegate.execute(command);
        }

        private int submissions() {
            return submissions.get();
        }
    }
}
