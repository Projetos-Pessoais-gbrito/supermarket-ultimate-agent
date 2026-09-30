package com.supermarketagent.catalog;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.ai.AiClient;
import com.supermarketagent.ai.AiUnavailableException;
import com.supermarketagent.catalog.ProductCategorizer.Answer;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ProductCategorizerTest {

    @MockitoBean
    private AiClient ai;

    @Autowired
    private ProductCategorizer categorizer;

    @Autowired
    private JdbcTemplate jdbc;

    private long bread;
    private long cheese;

    @BeforeEach
    void setUp() {
        jdbc.execute("TRUNCATE receipt_payments, receipt_items, receipts, store_products, products, stores, users "
                + "RESTART IDENTITY CASCADE");
        bread = insertProduct("PAO FRANCES CONG KG BALCAO", null, null);
        cheese = insertProduct("QJO MUSSARELA TIROLEZ FATIADO KG", null, null);
        when(ai.isAvailable()).thenReturn(true);
    }

    @Test
    void storesTheCategoryChosenForEachProduct() {
        answer(new Answer.Item(bread, ProductCategory.PADARIA), new Answer.Item(cheese, ProductCategory.LATICINIOS_E_FRIOS));

        assertThat(categorizer.categorizePending()).isEqualTo(2);

        assertThat(categoryOf(bread)).isEqualTo("PADARIA");
        assertThat(categoryOf(cheese)).isEqualTo("LATICINIOS_E_FRIOS");
    }

    @Test
    void sendsOnlyProductNamesAndSizes() {
        long flakes = insertProduct("FLOCAO DE MILHO DA TERRINHA", "400", "g");
        answer();

        categorizer.categorizePending();

        verify(ai).generateJson(contains(bread + ": PAO FRANCES CONG KG BALCAO"), anyMap(), eq(Answer.class));
        verify(ai).generateJson(contains(flakes + ": FLOCAO DE MILHO DA TERRINHA (400g)"), anyMap(), eq(Answer.class));
    }

    @Test
    void ignoresIdsThatWereNotRequested() {
        answer(new Answer.Item(9999, ProductCategory.BEBIDAS), new Answer.Item(bread, ProductCategory.PADARIA));

        assertThat(categorizer.categorizePending()).isEqualTo(1);
        assertThat(categoryOf(cheese)).as("unanswered products stay pending").isNull();
    }

    @Test
    void skipsCategorizedProductsOnTheNextRun() {
        answer(new Answer.Item(bread, ProductCategory.PADARIA), new Answer.Item(cheese, ProductCategory.LATICINIOS_E_FRIOS));
        categorizer.categorizePending();

        assertThat(categorizer.categorizePending()).isZero();
    }

    @Test
    void keepsProductsPendingWhenTheAiIsDown() {
        when(ai.generateJson(any(), anyMap(), eq(Answer.class))).thenThrow(new AiUnavailableException("quota"));

        assertThat(categorizer.categorizePending()).isZero();
        assertThat(categoryOf(bread)).isNull();
    }

    @Test
    void doesNothingWithoutAnAiProvider() {
        when(ai.isAvailable()).thenReturn(false);

        assertThat(categorizer.categorizePending()).isZero();
        verify(ai, never()).generateJson(any(), anyMap(), any());
    }

    private void answer(Answer.Item... items) {
        when(ai.generateJson(any(), anyMap(), eq(Answer.class))).thenReturn(new Answer(List.of(items)));
    }

    private long insertProduct(String name, String measureValue, String measureUnit) {
        return jdbc.queryForObject("""
                INSERT INTO products (normalized_name, measure_value, measure_unit)
                VALUES (?, CAST(? AS numeric), ?) RETURNING id""", Long.class, name, measureValue, measureUnit);
    }

    private String categoryOf(long productId) {
        return jdbc.queryForObject("SELECT category FROM products WHERE id = ?", String.class, productId);
    }
}
