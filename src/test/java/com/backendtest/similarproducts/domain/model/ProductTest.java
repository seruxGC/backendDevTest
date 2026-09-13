package com.backendtest.similarproducts.domain.model;

import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductTest {

    private static final ProductId PRODUCT_ID = new ProductId("1");
    private static final BigDecimal PRICE = new BigDecimal("19.99");

    @Test
    void rejectsNullId() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Product(null, "Dress", PRICE, true));
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsBlankName(String name) {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Product(PRODUCT_ID, name, PRICE, true));
    }

    @Test
    void rejectsNullPrice() {
        assertThatIllegalArgumentException()
                .isThrownBy(() -> new Product(PRODUCT_ID, "Dress", null, true));
    }
}
