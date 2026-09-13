package com.backendtest.similarproducts;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.util.concurrent.ExecutorService;

import com.backendtest.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogPort;
import com.backendtest.similarproducts.application.service.GetSimilarProductsService;
import com.backendtest.similarproducts.config.ProductDetailsExecutorConfig;
import com.backendtest.similarproducts.config.ProductsClientProperties;
import org.apache.hc.client5.http.impl.io.PoolingHttpClientConnectionManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest
class SimilarProductsApplicationTest {

    @Autowired
    private ProductsClientProperties properties;

    @Autowired
    @Qualifier(ProductDetailsExecutorConfig.PRODUCT_DETAILS_EXECUTOR)
    private ExecutorService productDetailsExecutor;

    @Autowired
    private GetSimilarProductsUseCase getSimilarProductsUseCase;

    @Autowired
    private ProductCatalogPort productCatalogPort;

    @Autowired
    private PoolingHttpClientConnectionManager productsConnectionManager;

    @Test
    void loadsProductionTopologyWithCriticalConfiguration() throws Exception {
        assertThat(properties.baseUrl()).isEqualTo(URI.create("http://localhost:3001"));
        assertThat(properties.maxConcurrencyPerRequest()).isEqualTo(10);
        assertThat(productsConnectionManager.getMaxTotal()).isEqualTo(600);
        assertThat(productsConnectionManager.getDefaultMaxPerRoute()).isEqualTo(600);
        assertThat(productDetailsExecutor.submit(() -> Thread.currentThread().isVirtual()).get()).isTrue();
        assertThat(getSimilarProductsUseCase).isInstanceOf(GetSimilarProductsService.class);
        assertThat(productCatalogPort).isNotNull();
    }
}
