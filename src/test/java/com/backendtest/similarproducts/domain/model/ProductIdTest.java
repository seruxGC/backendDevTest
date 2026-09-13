package com.backendtest.similarproducts.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatIllegalArgumentException;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class ProductIdTest {

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = " ")
    void rejectsBlankValues(String value) {
        assertThatIllegalArgumentException().isThrownBy(() -> new ProductId(value));
    }

    @Test
    void preservesOpaqueValue() {
        ProductId productId = new ProductId(" 01-A ");

        assertThat(productId.value()).isEqualTo(" 01-A ");
    }
}
