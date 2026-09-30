package com.supermarketagent.catalog.normalization;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.catalog.normalization.Measure.BaseUnit;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ProductNameNormalizerTest {

    @ParameterizedTest(name = "{0} → {1} / {2} {3}")
    @CsvSource(delimiter = '|', textBlock = """
            # description (from real receipts)  | name                         | amount | unit
            FLOCAO DE MILHO DA TERRINHA 400G    | FLOCAO DE MILHO DA TERRINHA  | 400    | GRAM
            CHOC MMS 132G POUCH AO LEITE        | CHOC MMS POUCH AO LEITE      | 132    | GRAM
            DOCE DE LEITE OLIVEIRA 80G XUP      | DOCE DE LEITE OLIVEIRA XUP   | 80     | GRAM
            ARROZ TIO JOAO TP1 5KG              | ARROZ TIO JOAO TP1           | 5000   | GRAM
            Açúcar Refinado União 1kg           | ACUCAR REFINADO UNIAO        | 1000   | GRAM
            OLEO SOJA LIZA 900ML                | OLEO SOJA LIZA               | 900    | MILLILITER
            REFRIG COCA COLA 2L                 | REFRIG COCA COLA             | 2000   | MILLILITER
            AGUA MINERAL 1,5 LT                 | AGUA MINERAL                 | 1500   | MILLILITER
            CERVEJA BRAHMA LATA 12X350ML        | CERVEJA BRAHMA LATA          | 4200   | MILLILITER
            LEITE UHT ITALAC 6 X 1L             | LEITE UHT ITALAC             | 6000   | MILLILITER
            """)
    void extractsNameAndPackageSize(String description, String name, BigDecimal amount, BaseUnit unit) {
        NormalizedProduct normalized = ProductNameNormalizer.normalize(description);

        assertThat(normalized.name()).isEqualTo(name);
        assertThat(normalized.measure()).contains(new Measure(amount, unit));
    }

    @ParameterizedTest(name = "{0} → {1}")
    @CsvSource(delimiter = '|', textBlock = """
            # sold by weight or without a size
            PAO FRANCES CONG KG BALCAO          | PAO FRANCES CONG KG BALCAO
            QJO MUSSARELA TIROLEZ FATIADO KG    | QJO MUSSARELA TIROLEZ FATIADO KG
            SACOLA PLASTICA CINZA 48X55         | SACOLA PLASTICA CINZA 48X55
            BANANA PRATA  (kg)                  | BANANA PRATA KG
            """)
    void keepsDescriptionsWithoutPackageSize(String description, String name) {
        NormalizedProduct normalized = ProductNameNormalizer.normalize(description);

        assertThat(normalized.name()).isEqualTo(name);
        assertThat(normalized.measure()).isEqualTo(Optional.empty());
    }
}
