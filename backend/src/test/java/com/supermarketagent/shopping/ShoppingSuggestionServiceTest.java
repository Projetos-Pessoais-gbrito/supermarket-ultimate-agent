package com.supermarketagent.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.shopping.ShoppingSuggestions.Suggestion;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class ShoppingSuggestionServiceTest {

    @Autowired
    private ShoppingSuggestionService service;

    @Autowired
    private JdbcTemplate jdbc;

    private ShoppingTestData data;
    private long ana;
    private long assai;
    private long carrefour;
    private long rice;
    private long riceAtAssai;
    private long riceAtCarrefour;

    @BeforeEach
    void setUp() {
        data = new ShoppingTestData(jdbc);
        data.reset();
        ana = data.user("ana@example.com");
        assai = data.store("ASSAI");
        carrefour = data.store("CARREFOUR");
        rice = data.product("ARROZ TIO JOAO 5KG");
        riceAtAssai = data.storeProduct(assai, rice);
        riceAtCarrefour = data.storeProduct(carrefour, rice);
    }

    @Test
    void suggestsAProductWhoseUsualIntervalHasPassed() {
        data.buy(ana, assai, "2026-08-01", riceAtAssai, "2", "25.00");
        data.buy(ana, carrefour, "2026-08-11", riceAtCarrefour, "2", "22.00");
        data.buy(ana, assai, "2026-08-21", riceAtAssai, "2", "25.00");

        Suggestion suggestion = single(LocalDate.parse("2026-09-01"));

        assertThat(suggestion.productId()).isEqualTo(rice);
        assertThat(suggestion.name()).isEqualTo("ARROZ TIO JOAO 5KG");
        assertThat(suggestion.averageIntervalDays()).isEqualTo(10);
        assertThat(suggestion.daysSinceLastPurchase()).isEqualTo(11);
        assertThat(suggestion.lastPurchase()).isEqualTo("2026-08-21");
        assertThat(suggestion.usualQuantity()).isEqualByComparingTo("2");
        assertThat(suggestion.lastPrice()).isEqualByComparingTo("25.00");
        assertThat(suggestion.bestRecentPrice()).isEqualByComparingTo("22.00");
        assertThat(suggestion.bestRecentStore()).isEqualTo("CARREFOUR");
        assertThat(suggestion.inList()).isFalse();
    }

    @Test
    void doesNotSuggestBeforeTheProductIsDue() {
        data.buy(ana, assai, "2026-08-01", riceAtAssai, "1", "25.00");
        data.buy(ana, assai, "2026-08-11", riceAtAssai, "1", "25.00");

        assertThat(service.suggestions(ana, LocalDate.parse("2026-08-15")).items()).isEmpty();   // 4 of 10 days
        assertThat(service.suggestions(ana, LocalDate.parse("2026-08-19")).items()).hasSize(1);  // 8 of 10 days
    }

    @Test
    void stopsSuggestingProductsTheUserNoLongerBuys() {
        data.buy(ana, assai, "2026-08-01", riceAtAssai, "1", "25.00");
        data.buy(ana, assai, "2026-08-11", riceAtAssai, "1", "25.00");

        assertThat(service.suggestions(ana, LocalDate.parse("2026-10-30")).items()).isEmpty();   // 80 of 10 days
    }

    @Test
    void needsAtLeastTwoPurchaseDays() {
        data.buy(ana, assai, "2026-08-01", riceAtAssai, "1", "25.00");
        data.buy(ana, carrefour, "2026-08-01", riceAtCarrefour, "1", "22.00");

        assertThat(service.suggestions(ana, LocalDate.parse("2026-09-01")).items()).isEmpty();
    }

    @Test
    void ignoresProductsBoughtAlmostEveryDay() {
        data.buy(ana, assai, "2026-08-01", riceAtAssai, "1", "25.00");
        data.buy(ana, assai, "2026-08-02", riceAtAssai, "1", "25.00");
        data.buy(ana, assai, "2026-08-03", riceAtAssai, "1", "25.00");

        assertThat(service.suggestions(ana, LocalDate.parse("2026-08-05")).items()).isEmpty();
    }

    @Test
    void ignoresUnmatchedItemsAndOtherUsers() {
        long bia = data.user("bia@example.com");
        long unmatched = data.storeProduct(assai, null);
        data.buy(ana, assai, "2026-08-01", unmatched, "1", "5.00");
        data.buy(ana, assai, "2026-08-11", unmatched, "1", "5.00");
        data.buy(bia, assai, "2026-08-01", riceAtAssai, "1", "25.00");
        data.buy(bia, assai, "2026-08-11", riceAtAssai, "1", "25.00");

        assertThat(service.suggestions(ana, LocalDate.parse("2026-08-25")).items()).isEmpty();
        assertThat(service.suggestions(bia, LocalDate.parse("2026-08-25")).items()).hasSize(1);
    }

    @Test
    void bestPriceOnlyLooksAtTheLastSixtyDays() {
        data.buy(ana, carrefour, "2026-05-01", riceAtCarrefour, "1", "15.00");
        data.buy(ana, assai, "2026-07-20", riceAtAssai, "1", "26.00");
        data.buy(ana, assai, "2026-08-21", riceAtAssai, "1", "25.00");

        Suggestion suggestion = single(LocalDate.parse("2026-10-20"));

        assertThat(suggestion.bestRecentPrice()).isEqualByComparingTo("25.00");
        assertThat(suggestion.bestRecentStore()).isEqualTo("ASSAI");
    }

    @Test
    void marksProductsAlreadyInTheOpenList() {
        data.buy(ana, assai, "2026-08-01", riceAtAssai, "1", "25.00");
        data.buy(ana, assai, "2026-08-11", riceAtAssai, "1", "25.00");
        jdbc.update("INSERT INTO shopping_list_items (user_id, product_id, name) VALUES (?, ?, 'ARROZ')", ana, rice);

        assertThat(single(LocalDate.parse("2026-08-25")).inList()).isTrue();
    }

    @Test
    void sortsTheMostOverdueFirst() {
        long coffee = data.product("CAFE PILAO 500G");
        long coffeeAtAssai = data.storeProduct(assai, coffee);
        data.buy(ana, assai, "2026-08-01", riceAtAssai, "1", "25.00");
        data.buy(ana, assai, "2026-08-21", riceAtAssai, "1", "25.00");       // every 20 days
        data.buy(ana, assai, "2026-08-11", coffeeAtAssai, "1", "18.00");
        data.buy(ana, assai, "2026-08-21", coffeeAtAssai, "1", "18.00");     // every 10 days

        assertThat(service.suggestions(ana, LocalDate.parse("2026-09-10")).items())
                .extracting(Suggestion::productId)
                .containsExactly(coffee, rice);
    }

    private Suggestion single(LocalDate today) {
        assertThat(service.suggestions(ana, today).items()).hasSize(1);
        return service.suggestions(ana, today).items().getFirst();
    }
}
