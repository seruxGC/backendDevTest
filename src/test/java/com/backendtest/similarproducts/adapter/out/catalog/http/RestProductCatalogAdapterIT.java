package com.backendtest.similarproducts.adapter.out.catalog.http;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.TimeUnit;

import com.backendtest.similarproducts.config.ProductsClientProperties;
import com.backendtest.similarproducts.config.ProductsRestClientConfig;
import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

class RestProductCatalogAdapterIT {

    private MockWebServer server;
    private PoolingHttpClientConnectionManager connectionManager;
    private CloseableHttpClient httpClient;
    private RestClient restClient;
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
                3,
                3,
                3);
        ProductsRestClientConfig config = new ProductsRestClientConfig();
        connectionManager = config.productsConnectionManager(properties);
        httpClient = config.productsHttpClient(connectionManager, properties);
        restClient = config.productsRestClient(httpClient, properties);
        adapter = new RestProductCatalogAdapter(restClient);
    }

    @AfterEach
    void tearDown() throws Exception {
        httpClient.close();
        connectionManager.close();
        server.shutdown();
    }

    @Test
    void retrievesNumericSimilarProductIds() throws Exception {
        server.enqueue(jsonResponse("[2,3,4]"));

        List<ProductId> result = adapter.getSimilarProductIds(new ProductId("1"));

        assertThat(result).extracting(ProductId::value).containsExactly("2", "3", "4");
        assertThat(server.takeRequest().getPath()).isEqualTo("/product/1/similarids");
    }

    @Test
    void retrievesAndMapsAProduct() throws Exception {
        server.enqueue(jsonResponse("""
                {"id":"2","name":"Dress","price":19.99,"availability":true}
                """));

        Product result = adapter.getProduct(new ProductId("2"));

        assertThat(result).isEqualTo(new Product(
                new ProductId("2"), "Dress", new BigDecimal("19.99"), true));
        assertThat(server.takeRequest().getPath()).isEqualTo("/product/2");
    }

    @Test
    void appliesConfiguredResponseTimeout() {
        server.enqueue(jsonResponse("[]").setHeadersDelay(500, TimeUnit.MILLISECONDS));

        assertThatThrownBy(() -> restClient.get()
                .uri("/product/1/similarids")
                .retrieve()
                .body(String.class))
                .isInstanceOf(ResourceAccessException.class);
    }

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
