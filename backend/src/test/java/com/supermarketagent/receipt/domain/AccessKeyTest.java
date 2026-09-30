package com.supermarketagent.receipt.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.YearMonth;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

class AccessKeyTest {

    // Synthetic keys built on the well-known test CNPJ 11.222.333/0001-81
    private static final String NFCE_KEY = "35260111222333000181650010000123451123456788";
    private static final String NFE_KEY = "35260111222333000181550010000000011000000011";

    @Test
    void exposesEachFieldOfTheKey() {
        AccessKey key = new AccessKey(NFCE_KEY);

        assertThat(key.stateCode()).isEqualTo("35");
        assertThat(key.issueYearMonth()).isEqualTo(YearMonth.of(2026, 1));
        assertThat(key.issuerCnpj()).isEqualTo("11222333000181");
        assertThat(key.model()).isEqualTo("65");
        assertThat(key.series()).isEqualTo(1);
        assertThat(key.number()).isEqualTo(12345);
        assertThat(key.emissionType()).isEqualTo('1');
        assertThat(key.checkDigit()).isEqualTo(8);
    }

    @Test
    void identifiesNfceByModel() {
        assertThat(new AccessKey(NFCE_KEY).isNfce()).isTrue();
        assertThat(new AccessKey(NFE_KEY).isNfce()).isFalse();
    }

    @Test
    void parseIgnoresSpacesAsPrintedOnReceipts() {
        AccessKey key = AccessKey.parse("3526 0111 2223 3300 0181 6500 1000 0123 4511 2345 6788");

        assertThat(key.value()).isEqualTo(NFCE_KEY);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"123", "3526011122233300018165001000012345112345678A"})
    void parseRejectsMalformedInput(String raw) {
        assertThatThrownBy(() -> AccessKey.parse(raw)).isInstanceOf(InvalidAccessKeyException.class);
    }

    @Test
    void rejectsKeyWithWrongLength() {
        assertThatThrownBy(() -> new AccessKey(NFCE_KEY + "0"))
                .isInstanceOf(InvalidAccessKeyException.class);
    }
}
