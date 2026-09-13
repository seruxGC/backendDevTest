package com.backendtest.similarproducts.config;

import java.net.URI;
import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("products.client")
public record ProductsClientProperties(
        URI baseUrl,
        Duration connectTimeout,
        Duration readTimeout,
        Duration connectionRequestTimeout,
        int maxConcurrencyPerRequest,
        int maxConnections,
        int maxConnectionsPerRoute) {
}
