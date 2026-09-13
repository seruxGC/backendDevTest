package com.backendtest.similarproducts.config;

import org.apache.hc.client5.http.config.ConnectionConfig;
import org.apache.hc.client5.http.config.RequestConfig;
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.client5.http.impl.classic.HttpClients;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManagerBuilder;
import org.apache.hc.core5.util.TimeValue;
import org.apache.hc.core5.util.Timeout;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.HttpComponentsClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class ProductsRestClientConfig {

    public static final String PRODUCTS_REST_CLIENT = "productsRestClient";

    @Bean(destroyMethod = "close")
    public PoolingHttpClientConnectionManager productsConnectionManager(
            ProductsClientProperties properties) {
        ConnectionConfig connectionConfig = ConnectionConfig.custom()
                .setConnectTimeout(Timeout.of(properties.connectTimeout()))
                .setValidateAfterInactivity(TimeValue.of(properties.validateAfterInactivity()))
                .build();

        return PoolingHttpClientConnectionManagerBuilder.create()
                .setMaxConnTotal(properties.maxConnections())
                .setMaxConnPerRoute(properties.maxConnectionsPerRoute())
                .setDefaultConnectionConfig(connectionConfig)
                .build();
    }

    @Bean(destroyMethod = "close")
    public CloseableHttpClient productsHttpClient(
            PoolingHttpClientConnectionManager connectionManager,
            ProductsClientProperties properties) {
        RequestConfig requestConfig = RequestConfig.custom()
                .setConnectionRequestTimeout(Timeout.of(properties.connectionRequestTimeout()))
                .setResponseTimeout(Timeout.of(properties.readTimeout()))
                .build();

        return HttpClients.custom()
                .setConnectionManager(connectionManager)
                .setDefaultRequestConfig(requestConfig)
                .evictExpiredConnections()
                .evictIdleConnections(TimeValue.of(properties.idleConnectionEvictTime()))
                .disableAutomaticRetries()
                .build();
    }

    @Bean(PRODUCTS_REST_CLIENT)
    public RestClient productsRestClient(
            CloseableHttpClient httpClient,
            ProductsClientProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .requestFactory(new HttpComponentsClientHttpRequestFactory(httpClient))
                .build();
    }
}
