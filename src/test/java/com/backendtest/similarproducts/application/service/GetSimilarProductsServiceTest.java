package com.backendtest.similarproducts.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import com.backendtest.similarproducts.application.error.SimilarProductsNotFoundException;
import com.backendtest.similarproducts.application.error.SimilarProductsTimeoutException;
import com.backendtest.similarproducts.application.error.SimilarProductsUnavailableException;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogException;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogFailure;
import com.backendtest.similarproducts.application.port.out.catalog.ProductCatalogPort;
import com.backendtest.similarproducts.application.port.out.telemetry.DetailFailureReason;
import com.backendtest.similarproducts.application.port.out.telemetry.SimilarProductsTelemetry;
import com.backendtest.similarproducts.application.port.out.telemetry.SimilarProductsTelemetryEvent;
import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

class GetSimilarProductsServiceTest {

    private static final ProductId REQUESTED_ID = new ProductId("1");
    private static final ProductId FIRST_ID = new ProductId("2");
    private static final ProductId SECOND_ID = new ProductId("3");

    private ProductCatalogPort productCatalog;
    private SimilarProductsTelemetry telemetry;
    private GetSimilarProductsService service;

    @BeforeEach
    void setUp() {
        productCatalog = mock(ProductCatalogPort.class);
        telemetry = mock(SimilarProductsTelemetry.class);
        service = new GetSimilarProductsService(productCatalog, telemetry, Runnable::run, 3);
    }

    @Test
    void rejectsNonPositiveConcurrencyLimit() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new GetSimilarProductsService(productCatalog, telemetry, Runnable::run, 0));
    }

    @Test
    void returnsEmptyResultWithoutFetchingDetailsWhenThereAreNoSimilarIds() {
        when(productCatalog.getSimilarProductIds(REQUESTED_ID)).thenReturn(List.of());

        List<Product> result = service.getSimilarProducts(REQUESTED_ID);

        assertThat(result).isEmpty();
        verify(productCatalog, never()).getProduct(org.mockito.ArgumentMatchers.any());
        verify(telemetry).record(org.mockito.ArgumentMatchers.argThat(event ->
                event.outcome() == SimilarProductsTelemetryEvent.Outcome.COMPLETE
                        && event.requestedDetailCount() == 0
                        && event.returnedDetailCount() == 0
                        && event.omissions().isEmpty()));
    }

    @ParameterizedTest
    @EnumSource(ProductCatalogFailure.class)
    void omitsAnExpectedFailureAndContinuesWithPendingIds(ProductCatalogFailure failure) {
        Product second = product(SECOND_ID, "Blazer");
        when(productCatalog.getSimilarProductIds(REQUESTED_ID)).thenReturn(List.of(FIRST_ID, SECOND_ID));
        when(productCatalog.getProduct(FIRST_ID)).thenThrow(catalogFailure(failure));
        when(productCatalog.getProduct(SECOND_ID)).thenReturn(second);
        GetSimilarProductsService singleWorkerService =
                new GetSimilarProductsService(productCatalog, telemetry, Runnable::run, 1);

        List<Product> result = singleWorkerService.getSimilarProducts(REQUESTED_ID);

        assertThat(result).containsExactly(second);
        verify(telemetry).record(org.mockito.ArgumentMatchers.argThat(event ->
                event.outcome() == SimilarProductsTelemetryEvent.Outcome.PARTIAL
                        && event.requestedDetailCount() == 2
                        && event.returnedDetailCount() == 1
                        && event.omissions().equals(Map.of(expectedDetailReason(failure), 1))));
    }

    @Test
    void returnsEmptyResultWhenAllDetailsAreNotFound() {
        givenSimilarIds(FIRST_ID, SECOND_ID);
        when(productCatalog.getProduct(FIRST_ID)).thenThrow(catalogFailure(ProductCatalogFailure.NOT_FOUND));
        when(productCatalog.getProduct(SECOND_ID)).thenThrow(catalogFailure(ProductCatalogFailure.NOT_FOUND));

        assertThat(service.getSimilarProducts(REQUESTED_ID)).isEmpty();
        verify(telemetry).record(org.mockito.ArgumentMatchers.argThat(event ->
                event.outcome() == SimilarProductsTelemetryEvent.Outcome.PARTIAL
                        && event.requestedDetailCount() == 2
                        && event.returnedDetailCount() == 0
                        && event.omissions().equals(Map.of(DetailFailureReason.NOT_FOUND, 2))));
    }

    @Test
    void timesOutWhenAllTechnicalDetailFailuresAreTimeouts() {
        givenSimilarIds(FIRST_ID, SECOND_ID);
        when(productCatalog.getProduct(FIRST_ID)).thenThrow(catalogFailure(ProductCatalogFailure.NOT_FOUND));
        when(productCatalog.getProduct(SECOND_ID)).thenThrow(catalogFailure(ProductCatalogFailure.TIMEOUT));

        assertThatThrownBy(() -> service.getSimilarProducts(REQUESTED_ID))
                .isInstanceOf(SimilarProductsTimeoutException.class);
    }

    @Test
    void becomesUnavailableWhenAnyTechnicalDetailFailureIsNotATimeout() {
        givenSimilarIds(FIRST_ID, SECOND_ID);
        when(productCatalog.getProduct(FIRST_ID)).thenThrow(catalogFailure(ProductCatalogFailure.TIMEOUT));
        when(productCatalog.getProduct(SECOND_ID)).thenThrow(catalogFailure(ProductCatalogFailure.SERVER_ERROR));

        assertThatThrownBy(() -> service.getSimilarProducts(REQUESTED_ID))
                .isInstanceOf(SimilarProductsUnavailableException.class);
        verify(telemetry).record(org.mockito.ArgumentMatchers.argThat(event ->
                event.outcome() == SimilarProductsTelemetryEvent.Outcome.FAILED
                        && event.requestedDetailCount() == 2
                        && event.returnedDetailCount() == 0
                        && event.omittedDetailCount() == 2));
    }

    @ParameterizedTest
    @MethodSource("similarIdsFailures")
    void mapsFailuresWhileFetchingSimilarIds(
            ProductCatalogFailure failure,
            Class<? extends RuntimeException> expectedException) {
        when(productCatalog.getSimilarProductIds(REQUESTED_ID)).thenThrow(catalogFailure(failure));

        assertThatThrownBy(() -> service.getSimilarProducts(REQUESTED_ID))
                .isInstanceOf(expectedException);
        verify(telemetry).record(org.mockito.ArgumentMatchers.argThat(event ->
                event.outcome() == SimilarProductsTelemetryEvent.Outcome.FAILED
                        && event.requestedDetailCount() == 0
                        && event.returnedDetailCount() == 0
                        && event.omissions().isEmpty()));
    }

    @Test
    void doesNotHideUnexpectedCatalogErrors() {
        givenSimilarIds(FIRST_ID);
        IllegalStateException unexpected = new IllegalStateException("unexpected");
        when(productCatalog.getProduct(FIRST_ID)).thenThrow(unexpected);

        assertThatThrownBy(() -> service.getSimilarProducts(REQUESTED_ID)).isSameAs(unexpected);
    }

    private void givenSimilarIds(ProductId... ids) {
        when(productCatalog.getSimilarProductIds(REQUESTED_ID)).thenReturn(List.of(ids));
    }

    private Product product(ProductId id, String name) {
        return new Product(id, name, new BigDecimal("19.99"), true);
    }

    private ProductCatalogException catalogFailure(ProductCatalogFailure failure) {
        return new ProductCatalogException(failure, "Catalog failure");
    }

    private DetailFailureReason expectedDetailReason(ProductCatalogFailure failure) {
        return switch (failure) {
            case NOT_FOUND -> DetailFailureReason.NOT_FOUND;
            case TIMEOUT -> DetailFailureReason.TIMEOUT;
            case CONNECTION_ERROR -> DetailFailureReason.CONNECTION_ERROR;
            case SERVER_ERROR, INVALID_RESPONSE -> DetailFailureReason.SERVER_ERROR;
        };
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
