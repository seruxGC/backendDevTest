package com.backendtest.similarproducts.application.service;

import static org.assertj.core.api.Assertions.assertThat;

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
class ProductDetailsFetcherConcurrencyTest {

    private static final int WAIT_SECONDS = 2;

    @Test
    void limitsWorkersAndConcurrentCallsPerRequest() throws Exception {
        List<ProductId> ids = productIds(6);
        CountDownLatch firstWorkersStarted = new CountDownLatch(2);
        CountDownLatch releaseCalls = new CountDownLatch(1);
        AtomicInteger activeCalls = new AtomicInteger();
        AtomicInteger maximumActiveCalls = new AtomicInteger();

        ProductCatalogPort catalog = catalog(id -> {
            int active = activeCalls.incrementAndGet();
            try {
                maximumActiveCalls.accumulateAndGet(active, Math::max);
                firstWorkersStarted.countDown();
                await(releaseCalls);
                return product(id);
            } finally {
                activeCalls.decrementAndGet();
            }
        });

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
                ExecutorService caller = Executors.newSingleThreadExecutor()) {
            CountingExecutor countingExecutor = new CountingExecutor(virtualExecutor);
            ProductDetailsFetcher fetcher = new ProductDetailsFetcher(catalog, countingExecutor, 2);

            CompletableFuture<List<Product>> result =
                    CompletableFuture.supplyAsync(() -> fetcher.fetch(ids), caller);

            try {
                assertThat(firstWorkersStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                assertThat(countingExecutor.submissions()).isEqualTo(2);
                assertThat(activeCalls).hasValue(2);
            } finally {
                releaseCalls.countDown();
            }

            assertThat(result.get(WAIT_SECONDS, TimeUnit.SECONDS))
                    .extracting(Product::id)
                    .containsExactlyElementsOf(ids);
            assertThat(maximumActiveCalls).hasValue(2);
        }
    }

    @Test
    void preservesInputOrderWhenResponsesArriveOutOfOrder() throws Exception {
        List<ProductId> ids = productIds(3);
        CountDownLatch allStarted = new CountDownLatch(3);
        Map<ProductId, CountDownLatch> releases = new ConcurrentHashMap<>();
        Map<ProductId, CountDownLatch> completions = new ConcurrentHashMap<>();
        ids.forEach(id -> {
            releases.put(id, new CountDownLatch(1));
            completions.put(id, new CountDownLatch(1));
        });

        ProductCatalogPort catalog = catalog(id -> {
            allStarted.countDown();
            await(releases.get(id));
            completions.get(id).countDown();
            return product(id);
        });

        try (ExecutorService virtualExecutor = Executors.newVirtualThreadPerTaskExecutor();
                ExecutorService caller = Executors.newSingleThreadExecutor()) {
            ProductDetailsFetcher fetcher = new ProductDetailsFetcher(catalog, virtualExecutor, 3);
            CompletableFuture<List<Product>> result =
                    CompletableFuture.supplyAsync(() -> fetcher.fetch(ids), caller);

            try {
                assertThat(allStarted.await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
                releaseAndAwait(ids.get(2), releases, completions);
                releaseAndAwait(ids.get(1), releases, completions);
                releaseAndAwait(ids.get(0), releases, completions);
            } finally {
                releases.values().forEach(CountDownLatch::countDown);
            }

            List<Product> products = result.get(WAIT_SECONDS, TimeUnit.SECONDS);
            assertThat(products).extracting(Product::id).containsExactlyElementsOf(ids);
        }
    }

    private ProductCatalogPort catalog(Function<ProductId, Product> fetchProduct) {
        return new ProductCatalogPort() {
            @Override
            public List<ProductId> getSimilarProductIds(ProductId productId) {
                return List.of();
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

    private static Product product(ProductId id) {
        return new Product(id, "Product " + id.value(), BigDecimal.ONE, true);
    }

    private void releaseAndAwait(
            ProductId id,
            Map<ProductId, CountDownLatch> releases,
        Map<ProductId, CountDownLatch> completions) throws InterruptedException {
        releases.get(id).countDown();
        assertThat(completions.get(id).await(WAIT_SECONDS, TimeUnit.SECONDS)).isTrue();
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(WAIT_SECONDS, TimeUnit.SECONDS)) {
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
