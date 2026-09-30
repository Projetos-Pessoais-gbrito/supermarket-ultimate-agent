package com.supermarketagent.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class StoreDisplayNameBackfillTest {

    @Autowired
    private StoreDisplayNameBackfill backfill;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void namesStoresImportedBeforeDisplayNamesExisted() {
        jdbc.execute("TRUNCATE receipt_payments, receipt_items, receipts, store_products, products, stores, users "
                + "RESTART IDENTITY CASCADE");
        jdbc.update("INSERT INTO stores (cnpj, name, state_code) VALUES ('06057223026480', 'SENDAS DISTRIBUIDORA S/A', '35')");
        jdbc.update("INSERT INTO stores (cnpj, name, display_name, state_code) VALUES ('67616128001550', 'AYUMI SUPERMERCADOS LTDA', 'CUSTOM', '35')");

        assertThat(backfill.backfill()).isEqualTo(1);

        assertThat(jdbc.queryForList("SELECT display_name FROM stores ORDER BY cnpj", String.class))
                .containsExactly("ASSAI", "CUSTOM");
    }
}
