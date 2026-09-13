package com.backendtest.similarproducts.adapter.out.catalog.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogException;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogFailure;
import com.backendtest.similarproducts.config.ProductsClientProperties;
import com.backendtest.similarproducts.config.ProductsRestClientConfig;
import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.web.client.RestClient;

class RestProductCatalogAdapterIT {

    private MockWebServer server;
    private PoolingHttpClientConnectionManager connectionManager;
    private CloseableHttpClient httpClient;
    private RestProductCatalogAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        ProductsClientProperties properties = new ProductsClientProperties(
                URI.create(server.url("/").toString()),
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                Duration.ofMillis(100),
                3,
                3,
                3);
        ProductsRestClientConfig config = new ProductsRestClientConfig();
        connectionManager = config.productsConnectionManager(properties);
        httpClient = config.productsHttpClient(connectionManager, properties);
        RestClient restClient = config.productsRestClient(httpClient, properties);
        adapter = new RestProductCatalogAdapter(restClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        httpClient.close();
        connectionManager.close();
        if (server != null) {
            server.shutdown();
        }
    }

    @Test
    void normalizesAndDeduplicatesSimilarProductIdsInOriginalOrder() throws Exception {
        server.enqueue(jsonResponse("[\"2\",3,\"2\",3,4]"));

        List<ProductId> result = adapter.getSimilarProductIds(new ProductId("1"));

        assertThat(result).extracting(ProductId::value).containsExactly("2", "3", "4");
        assertGetRequest("/product/1/similarids");
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "1.5", "true", "{}", "[]"})
    void rejectsInvalidSimilarProductIds(String invalidId) {
        server.enqueue(jsonResponse("[" + invalidId + "]"));

        assertThatThrownBy(() -> adapter.getSimilarProductIds(new ProductId("1")))
                .isInstanceOfSatisfying(ProductCatalogException.class, exception ->
                        assertThat(exception.failure()).isEqualTo(ProductCatalogFailure.INVALID_RESPONSE));
    }

    @Test
    void retrievesAndMapsAProduct() throws Exception {
        server.enqueue(jsonResponse("""
                {"id":"2","name":"Dress","price":19.99,"availability":true}
                """));

        Product result = adapter.getProduct(new ProductId("2"));

        assertThat(result).isEqualTo(new Product(
                new ProductId("2"), "Dress", new BigDecimal("19.99"), true));
        assertGetRequest("/product/2");
    }

    @ParameterizedTest
    @CsvSource({"404, NOT_FOUND", "500, SERVER_ERROR"})
    void mapsHttpFailures(int status, ProductCatalogFailure expectedFailure) {
        server.enqueue(new MockResponse().setResponseCode(status));

        assertCatalogFailure(
                () -> adapter.getSimilarProductIds(new ProductId("1")),
                expectedFailure);
    }

    @ParameterizedTest
    @ValueSource(strings = {"not-json", "{}"})
    void rejectsMalformedOrUnexpectedSimilarIdsResponses(String responseBody) {
        server.enqueue(jsonResponse(responseBody));

        assertCatalogFailure(
                () -> adapter.getSimilarProductIds(new ProductId("1")),
                ProductCatalogFailure.INVALID_RESPONSE);
    }

    @Test
    void rejectsInvalidProductResponses() {
        server.enqueue(jsonResponse("""
                {"id":"2","name":"Dress","price":19.99}
                """));

        assertCatalogFailure(
                () -> adapter.getProduct(new ProductId("2")),
                ProductCatalogFailure.INVALID_RESPONSE);
    }

    @Test
    void mapsConfiguredResponseTimeout() {
        server.enqueue(jsonResponse("[]").setHeadersDelay(500, TimeUnit.MILLISECONDS));

        assertCatalogFailure(
                () -> adapter.getSimilarProductIds(new ProductId("1")),
                ProductCatalogFailure.TIMEOUT);
    }

    @Test
    void mapsConnectionFailures() throws Exception {
        server.shutdown();
        server = null;

        assertCatalogFailure(
                () -> adapter.getSimilarProductIds(new ProductId("1")),
                ProductCatalogFailure.CONNECTION_ERROR);
    }

    @Test
    void evictsIdleConnections() throws Exception {
        server.enqueue(jsonResponse("[]"));
        adapter.getSimilarProductIds(new ProductId("1"));

        assertThat(connectionManager.getTotalStats().getAvailable()).isEqualTo(1);

        long deadline = System.nanoTime() + Duration.ofSeconds(3).toNanos();
        while (connectionManager.getTotalStats().getAvailable() != 0
                && System.nanoTime() < deadline) {
            Thread.sleep(25);
        }

        assertThat(connectionManager.getTotalStats().getAvailable()).isZero();
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }

    private void assertGetRequest(String path) throws InterruptedException {
        RecordedRequest request = server.takeRequest();
        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo(path);
    }

    private void assertCatalogFailure(Runnable request, ProductCatalogFailure expectedFailure) {
        assertThatThrownBy(request::run)
                .isInstanceOfSatisfying(ProductCatalogException.class, exception ->
                        assertThat(exception.failure()).isEqualTo(expectedFailure));
    }
}
