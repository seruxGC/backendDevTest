package com.backendtest.similarproducts;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.time.Duration;

import com.backendtest.similarproducts.config.ProductsClientProperties;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SimilarProductsApplicationTest {

    @Autowired
    private ProductsClientProperties properties;

    @Test
    void startsWithConfiguredProductClientProperties() {
        assertThat(properties.baseUrl()).isEqualTo(URI.create("http://localhost:3001"));
        assertThat(properties.connectTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.readTimeout()).isEqualTo(Duration.ofSeconds(7));
        assertThat(properties.connectionRequestTimeout()).isEqualTo(Duration.ofSeconds(1));
        assertThat(properties.maxConcurrencyPerRequest()).isEqualTo(10);
        assertThat(properties.maxConnections()).isEqualTo(600);
        assertThat(properties.maxConnectionsPerRoute()).isEqualTo(600);
    }
}
