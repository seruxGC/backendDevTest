package com.backendtest.similarproducts.adapter.out.catalog.http;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class RestProductCatalogAdapterIT {

    private MockWebServer server;
    private RestProductCatalogAdapter adapter;

    @BeforeEach
    void setUp() throws Exception {
        server = new MockWebServer();
        server.start();
        adapter = new RestProductCatalogAdapter(RestClient.builder()
                .baseUrl(server.url("/").toString())
                .build());
    }

    @AfterEach
    void tearDown() throws Exception {
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

    private MockResponse jsonResponse(String body) {
        return new MockResponse()
                .setHeader("Content-Type", "application/json")
                .setBody(body);
    }
}
