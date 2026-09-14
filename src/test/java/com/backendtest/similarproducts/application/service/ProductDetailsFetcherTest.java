package com.backendtest.similarproducts.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogException;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogFailure;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogPort;
import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class ProductDetailsFetcherTest {

    private static final ProductId FIRST_ID = new ProductId("2");
    private static final ProductId SECOND_ID = new ProductId("3");

    private ProductCatalogPort productCatalog;
    private ProductDetailsFetcher fetcher;

    @BeforeEach
    void setUp() {
        productCatalog = mock(ProductCatalogPort.class);
        fetcher = new ProductDetailsFetcher(productCatalog, Runnable::run, 1);
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void rejectsNonPositiveConcurrencyLimit(int concurrencyLimit) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new ProductDetailsFetcher(
                        productCatalog, Runnable::run, concurrencyLimit));
    }

    @Test
    void omitsCatalogFailureAndKeepsSuccessfulProducts() {
        Product product = product(SECOND_ID);
        when(productCatalog.getProduct(FIRST_ID)).thenThrow(catalogFailure());
        when(productCatalog.getProduct(SECOND_ID)).thenReturn(product);

        List<Product> result = fetcher.fetch(List.of(FIRST_ID, SECOND_ID));

        assertThat(result).containsExactly(product);
    }

    @Test
    void returnsEmptyWhenAllDetailRequestsFail() {
        when(productCatalog.getProduct(FIRST_ID)).thenThrow(catalogFailure());
        when(productCatalog.getProduct(SECOND_ID)).thenThrow(catalogFailure());

        List<Product> result = fetcher.fetch(List.of(FIRST_ID, SECOND_ID));

        assertThat(result).isEmpty();
    }

    @Test
    void propagatesUnexpectedDetailErrors() {
        when(productCatalog.getProduct(FIRST_ID)).thenThrow(new IllegalStateException("unexpected"));

        assertThatThrownBy(() -> fetcher.fetch(List.of(FIRST_ID)))
                .hasRootCauseInstanceOf(IllegalStateException.class);
    }

    private ProductCatalogException catalogFailure() {
        return new ProductCatalogException(ProductCatalogFailure.UNAVAILABLE, "Catalog failure");
    }

    private Product product(ProductId id) {
        return new Product(id, "Product " + id.value(), BigDecimal.ONE, true);
    }
}
