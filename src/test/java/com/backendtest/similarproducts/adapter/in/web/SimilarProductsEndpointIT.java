package com.backendtest.similarproducts.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import okhttp3.mockwebserver.Dispatcher;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SimilarProductsEndpointIT {

    private static final MockWebServer CATALOG = new MockWebServer();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void catalogProperties(DynamicPropertyRegistry registry) throws IOException {
        CATALOG.start();
        registry.add("products.client.base-url", () -> CATALOG.url("/").toString());
        CATALOG.setDispatcher(new CatalogDispatcher());
    }

    @AfterAll
    static void stopCatalog() throws IOException {
        CATALOG.shutdown();
    }

    @Test
    void servesThePublicContractThroughTheCompleteApplication() throws Exception {
        HttpResponse<String> response = get("/product/1/similar");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Content-Type"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("application/json"));
        assertThat(response.body()).isEqualTo("""
                [{"id":"2","name":"Dress","price":19.99,"availability":true},{"id":"3","name":"Shirt","price":7.5,"availability":false}]""");
    }

    @Test
    void servesCatalogNotFoundAsAnEmptyPublicError() throws Exception {
        HttpResponse<String> response = get("/product/missing/similar");

        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).isEmpty();
    }

    @Test
    void exposesApplicationMetricsThroughActuator() throws Exception {
        get("/product/1/similar");

        HttpResponse<String> response = get("/actuator/metrics/similar.products.requests");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("similar.products.requests", "outcome");
    }

    private HttpResponse<String> get(String path) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .GET()
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }

    private static final class CatalogDispatcher extends Dispatcher {

        @Override
        public MockResponse dispatch(RecordedRequest request) {
            return switch (request.getPath()) {
                case "/product/1/similarids" -> json("[\"2\",\"3\",\"2\"]");
                case "/product/2" -> json("""
                        {"id":"2","name":"Dress","price":19.99,"availability":true}""");
                case "/product/3" -> json("""
                        {"id":"3","name":"Shirt","price":7.5,"availability":false}""");
                case "/product/missing/similarids" -> new MockResponse().setResponseCode(404);
                default -> new MockResponse().setResponseCode(500);
            };
        }

        private MockResponse json(String body) {
            return new MockResponse()
                    .setHeader("Content-Type", "application/json")
                    .setBody(body);
        }
    }
}
