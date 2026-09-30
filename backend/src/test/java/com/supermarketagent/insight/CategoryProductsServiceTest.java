package com.supermarketagent.insight;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.insight.CategoryProducts.Product;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class CategoryProductsServiceTest {

    private static final YearMonth SEPTEMBER = YearMonth.of(2026, 9);

    @Autowired
    private CategoryProductsService service;

    @Autowired
    private SpendingInsightService spending;

    @Autowired
    private JdbcTemplate jdbc;

    private InsightTestData data;
    private long ana;
    private long assai;
    private long carrefour;
    private long riceAtAssai;
    private long riceAtCarrefour;
    private long coffeeAtAssai;
    private long unlinkedSoap;

    @BeforeEach
    void setUp() {
        data = new InsightTestData(jdbc);
        data.reset();
        ana = data.user("ana@example.com");
        assai = data.store("SENDAS DISTRIBUIDORA S/A", "ASSAI");
        carrefour = data.store("CARREFOUR COMERCIO E INDUSTRIA LTDA", "CARREFOUR");
        long rice = data.product("ARROZ TIO JOAO TP1", "MERCEARIA");
        riceAtAssai = data.storeProduct(assai, rice, "ARROZ TIO JOAO TP1 5KG");
        riceAtCarrefour = data.storeProduct(carrefour, rice, "ARROZ T.JOAO TP1 5KG");
        coffeeAtAssai = data.storeProduct(assai, data.product("CAFE PILAO", "MERCEARIA"), "CAFE PILAO 500G");
        unlinkedSoap = data.storeProduct(carrefour, null, "SABAO EM PO OMO 1KG");
    }

    @Test
    void listsTheProductsOfACategoryLargestSpendingFirst() {
        data.receipt(ana, assai, "2026-08-10T15:00:00Z", riceAtAssai, "2", "25.00");
        data.receipt(ana, carrefour, "2026-09-10T15:00:00Z", riceAtCarrefour, "1", "30.00");
        data.receipt(ana, assai, "2026-09-12T15:00:00Z", coffeeAtAssai, "1", "18.00");

        CategoryProducts result = service.products(ana, "MERCEARIA", SEPTEMBER, 6).orElseThrow();

        assertThat(result.label()).isEqualTo("Mercearia");
        assertThat(result.total()).isEqualByComparingTo("98.00");
        assertThat(result.products()).extracting(Product::name).containsExactly("ARROZ TIO JOAO TP1", "CAFE PILAO");
        Product rice = result.products().getFirst();
        assertThat(rice.timesBought()).isEqualTo(2);
        assertThat(rice.totalSpent()).isEqualByComparingTo("80.00");
        assertThat(rice.lastUnitPrice()).isEqualByComparingTo("30.00");
        assertThat(rice.lastStoreName()).isEqualTo("CARREFOUR");
    }

    @Test
    void uncategorizedIncludesItemsNotMatchedToAProductYet() {
        data.receipt(ana, carrefour, "2026-09-10T15:00:00Z", unlinkedSoap, "1", "22.90");

        CategoryProducts result = service.products(ana, "none", SEPTEMBER, 6).orElseThrow();

        assertThat(result.label()).isEqualTo("Sem categoria");
        assertThat(result.products()).extracting(Product::name).containsExactly("SABAO EM PO OMO 1KG");
        assertThat(result.products().getFirst().productId()).isNull();
    }

    @Test
    void totalsMatchTheDashboardBars() {
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", riceAtAssai, "2", "25.00");
        data.receipt(ana, carrefour, "2026-09-11T15:00:00Z", unlinkedSoap, "1", "22.90");

        for (var bar : spending.spending(ana, SEPTEMBER, 6).byCategory()) {
            String key = bar.category() == null ? "none" : bar.category();
            assertThat(service.products(ana, key, SEPTEMBER, 6).orElseThrow().total())
                    .as(bar.label()).isEqualByComparingTo(bar.total());
        }
    }

    @Test
    void onlyShowsTheUsersOwnPurchasesInThePeriod() {
        long bia = data.user("bia@example.com");
        data.receipt(bia, assai, "2026-09-10T15:00:00Z", riceAtAssai, "1", "25.00");
        data.receipt(ana, assai, "2026-01-10T15:00:00Z", coffeeAtAssai, "1", "18.00"); // outside 6 months

        assertThat(service.products(ana, "MERCEARIA", SEPTEMBER, 6).orElseThrow().products()).isEmpty();
    }

    @Test
    void rejectsUnknownCategories() {
        assertThat(service.products(ana, "DROP TABLE", SEPTEMBER, 6)).isEmpty();
    }
}
