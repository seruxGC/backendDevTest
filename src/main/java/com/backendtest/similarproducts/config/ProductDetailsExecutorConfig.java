package com.backendtest.similarproducts.config;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration(proxyBeanMethods = false)
public class ProductDetailsExecutorConfig {

    public static final String PRODUCT_DETAILS_EXECUTOR = "productDetailsExecutor";

    @Bean(name = PRODUCT_DETAILS_EXECUTOR, destroyMethod = "close")
    ExecutorService productDetailsExecutor() {
        return Executors.newVirtualThreadPerTaskExecutor();
    }
}
