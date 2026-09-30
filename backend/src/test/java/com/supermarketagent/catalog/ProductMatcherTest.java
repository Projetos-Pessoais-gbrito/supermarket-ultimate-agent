package com.supermarketagent.catalog;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.TestcontainersConfiguration;
import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProductMatcherTest {

    @Autowired
    private ProductMatcher matcher;

    @Autowired
    private JdbcTemplate jdbc;

    private long assai;
    private long carrefour;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE receipt_payments, receipt_items, receipts, store_products, products, stores, users "
                + "RESTART IDENTITY CASCADE");
        assai = insertStore("06057223000171", "ASSAI");
        carrefour = insertStore("45543915000181", "CARREFOUR");
    }

    @Test
    void linksSpellingVariantsOfTheSameProductAcrossStores() {
        long a = insertStoreProduct(assai, "1", "ARROZ TIO JOAO TP1 5KG");
        long b = insertStoreProduct(carrefour, "99", "ARROZ T.JOAO TP1 5KG");

        matcher.linkUnlinkedStoreProducts();

        assertThat(productOf(a)).isEqualTo(productOf(b));
    }

    @Test
    void keepsDifferentPackageSizesApart() {
        long small = insertStoreProduct(assai, "1", "ARROZ TIO JOAO TP1 1KG");
        long large = insertStoreProduct(assai, "2", "ARROZ TIO JOAO TP1 5KG");

        matcher.linkUnlinkedStoreProducts();

        assertThat(productOf(small)).isNotEqualTo(productOf(large));
    }

    @Test
    void keepsDifferentBrandsApart() {
        long italac = insertStoreProduct(assai, "1", "LEITE UHT ITALAC INTEGRAL 1L");
        long piracanjuba = insertStoreProduct(carrefour, "2", "LEITE UHT PIRACANJUBA INTEGRAL 1L");

        matcher.linkUnlinkedStoreProducts();

        assertThat(productOf(italac)).isNotEqualTo(productOf(piracanjuba));
    }

    @Test
    void storesNormalizedNameAndPackageSize() {
        long id = insertStoreProduct(assai, "1", "Flocão de Milho da Terrinha 400g");

        matcher.linkUnlinkedStoreProducts();

        var product = jdbc.queryForMap("SELECT normalized_name, measure_value, measure_unit FROM products WHERE id = ?",
                productOf(id));
        assertThat(product.get("normalized_name")).isEqualTo("FLOCAO DE MILHO DA TERRINHA");
        assertThat((BigDecimal) product.get("measure_value")).isEqualByComparingTo("400");
        assertThat(product.get("measure_unit")).isEqualTo("g");
    }

    @Test
    void linksItemsSoldByWeightByNameOnly() {
        long a = insertStoreProduct(assai, "1", "PRESUNTO AURORA FAT KG");
        long b = insertStoreProduct(carrefour, "2", "PRESUNTO AURORA FATIADO KG");

        matcher.linkUnlinkedStoreProducts();

        assertThat(productOf(a)).isEqualTo(productOf(b));
    }

    @Test
    void processesOnlyUnlinkedStoreProducts() {
        insertStoreProduct(assai, "1", "ARROZ TIO JOAO TP1 5KG");

        assertThat(matcher.linkUnlinkedStoreProducts()).isEqualTo(1);
        assertThat(matcher.linkUnlinkedStoreProducts()).isZero();
    }

    private long insertStore(String cnpj, String name) {
        return jdbc.queryForObject(
                "INSERT INTO stores (cnpj, name, state_code) VALUES (?, ?, '35') RETURNING id", Long.class, cnpj, name);
    }

    private long insertStoreProduct(long storeId, String code, String description) {
        return jdbc.queryForObject("""
                INSERT INTO store_products (store_id, store_code, description, unit)
                VALUES (?, ?, ?, 'UN') RETURNING id""", Long.class, storeId, code, description);
    }

    private Long productOf(long storeProductId) {
        Long productId = jdbc.queryForObject(
                "SELECT product_id FROM store_products WHERE id = ?", Long.class, storeProductId);
        assertThat(productId).as("store product %d should be linked", storeProductId).isNotNull();
        return productId;
    }
}
