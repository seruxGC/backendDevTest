package com.backendtest.similarproducts.adapter.in.web;

import static org.hamcrest.Matchers.hasSize;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.Stream;

import com.backendtest.similarproducts.application.error.SimilarProductsNotFoundException;
import com.backendtest.similarproducts.application.error.SimilarProductsTimeoutException;
import com.backendtest.similarproducts.application.error.SimilarProductsUnavailableException;
import com.backendtest.similarproducts.application.port.in.GetSimilarProductsUseCase;
import com.backendtest.similarproducts.domain.model.Product;
import com.backendtest.similarproducts.domain.model.ProductId;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class SimilarProductsControllerTest {

    @Mock
    private GetSimilarProductsUseCase useCase;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .standaloneSetup(new SimilarProductsController(useCase))
                .setControllerAdvice(new SimilarProductsExceptionHandler())
                .build();
    }

    @Test
    void exposesOrderedProductsUsingOnlyTheContractFields() throws Exception {
        when(useCase.getSimilarProducts(any())).thenReturn(List.of(
                product("20", "T-shirt", "12.50", true),
                product("3", "Dress", "24.99", false)));

        mockMvc.perform(get("/product/1/similar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].*", hasSize(4)))
                .andExpect(jsonPath("$[0].id").value("20"))
                .andExpect(jsonPath("$[0].name").value("T-shirt"))
                .andExpect(jsonPath("$[0].price").value(12.50))
                .andExpect(jsonPath("$[0].availability").value(true))
                .andExpect(jsonPath("$[1].id").value("3"))
                .andExpect(jsonPath("$[1].availability").value(false));

        ArgumentCaptor<ProductId> productId = ArgumentCaptor.forClass(ProductId.class);
        verify(useCase).getSimilarProducts(productId.capture());
        org.assertj.core.api.Assertions.assertThat(productId.getValue().value()).isEqualTo("1");
    }

    @Test
    void returnsAnEmptyJsonArrayWhenThereAreNoSimilarProducts() throws Exception {
        when(useCase.getSimilarProducts(any())).thenReturn(List.of());

        mockMvc.perform(get("/product/1/similar"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(content().json("[]"));
    }

    @Test
    void rejectsMethodsOutsideThePublicContract() throws Exception {
        mockMvc.perform(post("/product/1/similar"))
                .andExpect(status().isMethodNotAllowed());

        verify(useCase, never()).getSimilarProducts(any());
    }

    @ParameterizedTest
    @MethodSource("publicErrors")
    void mapsApplicationErrorsWithoutAResponseBody(RuntimeException error, int expectedStatus)
            throws Exception {
        when(useCase.getSimilarProducts(any())).thenThrow(error);

        mockMvc.perform(get("/product/1/similar"))
                .andExpect(status().is(expectedStatus))
                .andExpect(content().string(""));
    }

    private static Stream<Arguments> publicErrors() {
        return Stream.of(
                Arguments.of(new SimilarProductsNotFoundException("not found", null), 404),
                Arguments.of(new SimilarProductsUnavailableException("unavailable"), 502),
                Arguments.of(new SimilarProductsTimeoutException("timeout"), 504));
    }

    private Product product(String id, String name, String price, boolean availability) {
        return new Product(new ProductId(id), name, new BigDecimal(price), availability);
    }
}
