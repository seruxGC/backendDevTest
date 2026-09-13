package com.backendtest.similarproducts.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestClient;

@Configuration(proxyBeanMethods = false)
public class ProductsRestClientConfig {

    public static final String PRODUCTS_REST_CLIENT = "productsRestClient";

    @Bean(PRODUCTS_REST_CLIENT)
    RestClient productsRestClient(ProductsClientProperties properties) {
        return RestClient.builder()
                .baseUrl(properties.baseUrl().toString())
                .build();
    }
}
