package com.supermarketagent.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class StoreNamesTest {

    @ParameterizedTest(name = "{1} → {2}")
    @CsvSource(delimiter = '|', textBlock = """
            # CNPJ           | legal name on SEFAZ                       | display name
            06057223026480   | SENDAS DISTRIBUIDORA S/A                  | ASSAI
            06057223000171   | Sendas Distribuidora S.A.                 | ASSAI
            99999999000191   | SENDAS DISTRIBUIDORA S/A                  | ASSAI
            11111111000191   | ATACADÃO S.A.                             | ATACADAO
            22222222000191   | CARREFOUR COMERCIO E INDUSTRIA LTDA       | CARREFOUR
            67616128001550   | AYUMI SUPERMERCADOS LTDA                  | AYUMI SUPERMERCADOS
            33333333000191   | MERCADINHO BOM PRECO EIRELI               | MERCADINHO BOM PRECO
            44444444000191   | SUPERMERCADO EXEMPLO LTDA ME              | SUPERMERCADO EXEMPLO
            55555555000191   | EMPORIO SANTA MARIA                       | EMPORIO SANTA MARIA
            """)
    void showsTheNameShoppersKnow(String cnpj, String legalName, String displayName) {
        assertThat(StoreNames.displayName(cnpj, legalName)).isEqualTo(displayName);
    }

    @ParameterizedTest
    @CsvSource(delimiter = '|', textBlock = """
            66666666000191 | LTDA
            """)
    void keepsTheLegalNameWhenNothingIsLeft(String cnpj, String legalName) {
        assertThat(StoreNames.displayName(cnpj, legalName)).isEqualTo("LTDA");
    }
}
