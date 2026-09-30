package com.supermarketagent.receipt.query;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.insight.InsightTestData;
import com.supermarketagent.receipt.query.ReceiptSearch.Filter;
import com.supermarketagent.receipt.query.ReceiptSearch.MonthlyTotal;
import com.supermarketagent.receipt.query.ReceiptSearch.Result;
import java.math.BigDecimal;
import java.time.YearMonth;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ReceiptSearchServiceTest {

    @Autowired
    private ReceiptSearchService service;

    @Autowired
    private JdbcTemplate jdbc;

    private long ana;
    private long augustAssai;
    private long septemberAssai;
    private long septemberCarrefour;

    @BeforeEach
    void setUp() {
        InsightTestData data = new InsightTestData(jdbc);
        data.reset();
        ana = data.user("ana@example.com");
        long assai = data.store("SENDAS DISTRIBUIDORA S/A", "Assaí");
        long carrefour = data.store("CARREFOUR COMERCIO LTDA", "Carrefour");
        long coffee = data.storeProduct(assai, data.product("CAFE", "BEBIDAS"), "CAFE PILAO 500G");
        long rice = data.storeProduct(assai, data.product("ARROZ", "MERCEARIA"), "ARROZ TIO JOAO 5KG");
        jdbc.update("UPDATE products SET display_name = 'Pão de queijo' WHERE normalized_name = 'ARROZ'");
        long bread = data.storeProduct(carrefour, null, "PAO FRANCES KG");
        augustAssai = data.receipt(ana, assai, "2026-08-10T15:00:00Z", coffee, "1", "20.00");
        septemberAssai = data.receipt(ana, assai, "2026-09-05T15:00:00Z", rice, "1", "30.00");
        septemberCarrefour = data.receipt(ana, carrefour, "2026-09-20T15:00:00Z", bread, "1", "12.50");
        long bia = data.user("bia@example.com");
        data.receipt(bia, assai, "2026-09-06T15:00:00Z", coffee, "1", "99.00");
    }

    @Test
    void listsOnlyTheUsersReceiptsNewestFirstWithMonthlyTotals() {
        Result result = service.search(ana, Filter.NONE, 0, 20);

        assertThat(result.content()).extracting(ReceiptSummary::id)
                .containsExactly(septemberCarrefour, septemberAssai, augustAssai);
        assertThat(result.content().getFirst().storeName()).isEqualTo("Carrefour");
        assertThat(result.content().getFirst().itemCount()).isEqualTo(1);
        assertThat(result.totalElements()).isEqualTo(3);
        assertThat(result.monthlyTotals()).containsExactly(
                new MonthlyTotal("2026-09", new BigDecimal("42.50"), 2),
                new MonthlyTotal("2026-08", new BigDecimal("20.00"), 1));
        assertThat(result.stores()).containsExactly("Assaí", "Carrefour");
        assertThat(result.months()).containsExactly("2026-09", "2026-08");
    }

    @Test
    void filtersByStoreName() {
        Result result = service.search(ana, new Filter("Assaí", null, null), 0, 20);

        assertThat(result.content()).extracting(ReceiptSummary::id).containsExactly(septemberAssai, augustAssai);
        assertThat(result.stores()).containsExactly("Assaí", "Carrefour");
    }

    @Test
    void filtersBySaoPauloMonth() {
        Result result = service.search(ana, new Filter(null, YearMonth.of(2026, 9), null), 0, 20);

        assertThat(result.content()).extracting(ReceiptSummary::id)
                .containsExactly(septemberCarrefour, septemberAssai);
        assertThat(result.monthlyTotals()).singleElement().extracting(MonthlyTotal::month).isEqualTo("2026-09");
    }

    @Test
    void searchesItemDescriptionsAndProductNamesIgnoringCaseAndAccents() {
        assertThat(service.search(ana, new Filter(null, null, "pilão"), 0, 20).content())
                .extracting(ReceiptSummary::id).containsExactly(augustAssai);
        assertThat(service.search(ana, new Filter(null, null, "PAO DE QUEIJO"), 0, 20).content())
                .extracting(ReceiptSummary::id).containsExactly(septemberAssai);
        assertThat(service.search(ana, new Filter(null, null, "pão"), 0, 20).content())
                .extracting(ReceiptSummary::id).containsExactly(septemberCarrefour, septemberAssai);
    }

    @Test
    void treatsLikeWildcardsAsPlainText() {
        assertThat(service.search(ana, new Filter(null, null, "%"), 0, 20).content()).isEmpty();
    }

    @Test
    void combinesFilters() {
        Result result = service.search(ana, new Filter("Assaí", YearMonth.of(2026, 9), "arroz"), 0, 20);

        assertThat(result.content()).extracting(ReceiptSummary::id).containsExactly(septemberAssai);
        assertThat(service.search(ana, new Filter("Carrefour", YearMonth.of(2026, 8), null), 0, 20).content())
                .isEmpty();
    }

    @Test
    void paginatesWhileTotalsCoverEveryMatch() {
        Result first = service.search(ana, Filter.NONE, 0, 2);
        Result second = service.search(ana, Filter.NONE, 1, 2);

        assertThat(first.content()).extracting(ReceiptSummary::id).containsExactly(septemberCarrefour, septemberAssai);
        assertThat(second.content()).extracting(ReceiptSummary::id).containsExactly(augustAssai);
        assertThat(second.totalPages()).isEqualTo(2);
        assertThat(second.monthlyTotals()).hasSize(2);
    }

    @Test
    void neverShowsOtherUsersReceiptsEvenWhenSearching() {
        assertThat(service.search(ana, new Filter(null, null, "cafe"), 0, 20).content())
                .extracting(ReceiptSummary::id).containsExactly(augustAssai);
    }
}
