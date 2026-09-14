package com.backendtest.similarproducts.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import com.backendtest.similarproducts.application.error.SimilarProductsNotFoundException;
import com.backendtest.similarproducts.application.error.SimilarProductsTimeoutException;
import com.backendtest.similarproducts.application.error.SimilarProductsUnavailableException;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogException;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogFailure;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogPort;
import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GetSimilarProductsServiceTest {

    private static final ProductId REQUESTED_ID = new ProductId("1");
    private static final ProductId FIRST_ID = new ProductId("2");
    private static final ProductId SECOND_ID = new ProductId("3");

    private ProductCatalogPort productCatalog;
    private ProductDetailsFetcher productDetailsFetcher;
    private GetSimilarProductsService service;

    @BeforeEach
    void setUp() {
        productCatalog = mock(ProductCatalogPort.class);
        productDetailsFetcher = mock(ProductDetailsFetcher.class);
        service = new GetSimilarProductsService(productCatalog, productDetailsFetcher);
    }

    @Test
    void returnsEmptyResultWithoutFetchingDetailsWhenThereAreNoSimilarIds() {
        when(productCatalog.getSimilarProductIds(REQUESTED_ID)).thenReturn(List.of());

        List<Product> result = service.getSimilarProducts(REQUESTED_ID);

        assertThat(result).isEmpty();
        verifyNoInteractions(productDetailsFetcher);
    }

    @Test
    void delegatesSimilarIdsToTheDetailsFetcher() {
        List<ProductId> similarIds = List.of(FIRST_ID, SECOND_ID);
        List<Product> products = List.of(product(SECOND_ID, "Blazer"));
        when(productCatalog.getSimilarProductIds(REQUESTED_ID)).thenReturn(similarIds);
        when(productDetailsFetcher.fetch(similarIds)).thenReturn(products);

        List<Product> result = service.getSimilarProducts(REQUESTED_ID);

        assertThat(result).isSameAs(products);
        verify(productDetailsFetcher).fetch(similarIds);
    }

    @ParameterizedTest
    @MethodSource("similarIdsFailures")
    void mapsFailuresWhileFetchingSimilarIds(
            ProductCatalogFailure failure,
            Class<? extends RuntimeException> expectedException) {
        when(productCatalog.getSimilarProductIds(REQUESTED_ID)).thenThrow(catalogFailure(failure));

        assertThatThrownBy(() -> service.getSimilarProducts(REQUESTED_ID))
                .isInstanceOf(expectedException);
    }

    private Product product(ProductId id, String name) {
        return new Product(id, name, new BigDecimal("19.99"), true);
    }

    private ProductCatalogException catalogFailure(ProductCatalogFailure failure) {
        return new ProductCatalogException(failure, "Catalog failure");
    }

    private static Stream<Arguments> similarIdsFailures() {
        return Stream.of(
                Arguments.of(ProductCatalogFailure.NOT_FOUND, SimilarProductsNotFoundException.class),
                Arguments.of(ProductCatalogFailure.TIMEOUT, SimilarProductsTimeoutException.class),
                Arguments.of(ProductCatalogFailure.SERVER_ERROR, SimilarProductsUnavailableException.class),
                Arguments.of(ProductCatalogFailure.CONNECTION_ERROR, SimilarProductsUnavailableException.class),
                Arguments.of(ProductCatalogFailure.INVALID_RESPONSE, SimilarProductsUnavailableException.class));
    }
}
