package com.supermarketagent.catalog.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.catalog.normalization.UnitPrice.Per;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class UnitPriceTest {

    @Test
    void itemsSoldByWeightAlreadyHaveAPricePerKilogram() {
        assertThat(UnitPrice.of("KG", new BigDecimal("17.99"), Optional.empty()))
                .contains(new UnitPrice(new BigDecimal("17.99"), Per.KILOGRAM));
    }

    @Test
    void packagedItemsAreConvertedToPricePerKilogram() {
        // FLOCAO DE MILHO DA TERRINHA 400G at R$ 1,99
        var measure = ProductNameNormalizer.normalize("FLOCAO DE MILHO DA TERRINHA 400G").measure();

        assertThat(UnitPrice.of("UN", new BigDecimal("1.99"), measure))
                .contains(new UnitPrice(new BigDecimal("4.98"), Per.KILOGRAM));
    }

    @Test
    void liquidsAreConvertedToPricePerLiter() {
        var measure = ProductNameNormalizer.normalize("CERVEJA BRAHMA LATA 12X350ML").measure();

        assertThat(UnitPrice.of("UN", new BigDecimal("39.90"), measure))
                .contains(new UnitPrice(new BigDecimal("9.50"), Per.LITER));
    }

    @Test
    void comparesPackageSizesFairly() {
        var small = UnitPrice.of("UN", new BigDecimal("6.49"), ProductNameNormalizer.normalize("ARROZ TIO JOAO 1KG").measure());
        var large = UnitPrice.of("UN", new BigDecimal("27.90"), ProductNameNormalizer.normalize("ARROZ TIO JOAO 5KG").measure());

        assertThat(large.orElseThrow().amount()).isLessThan(small.orElseThrow().amount());
    }

    @Test
    void isUnknownWithoutAPackageSize() {
        assertThat(UnitPrice.of("UN", new BigDecimal("0.18"), Optional.empty())).isEmpty();
    }
}
