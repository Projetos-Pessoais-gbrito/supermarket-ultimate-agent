package com.supermarketagent.shopping;

import static org.assertj.core.api.Assertions.assertThat;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.shopping.PriceAlerts.Alert;
import com.supermarketagent.shopping.PriceAlerts.Type;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@SpringBootTest
@Import(TestcontainersConfiguration.class)
class PriceAlertServiceTest {

    private static final LocalDate TODAY = LocalDate.parse("2026-08-10");

    @Autowired
    private PriceAlertService service;

    @Autowired
    private JdbcTemplate jdbc;

    private ShoppingTestData data;
    private long ana;
    private long assai;
    private long carrefour;
    private long milk;
    private long milkAtAssai;
    private long milkAtCarrefour;

    @BeforeEach
    void setUp() {
        data = new ShoppingTestData(jdbc);
        data.reset();
        ana = data.user("ana@example.com");
        assai = data.store("ASSAI");
        carrefour = data.store("CARREFOUR");
        milk = data.product("LEITE ITALAC 1L");
        milkAtAssai = data.storeProduct(assai, milk);
        milkAtCarrefour = data.storeProduct(carrefour, milk);
    }

    @Test
    void flagsADealWhenTheLatestPriceIsWellBelowTheUsualOne() {
        data.buy(ana, assai, "2026-07-01", milkAtAssai, "1", "5.00");
        data.buy(ana, assai, "2026-07-15", milkAtAssai, "1", "5.00");
        data.buy(ana, carrefour, "2026-08-01", milkAtCarrefour, "1", "4.25");

        Alert alert = single();

        assertThat(alert.productId()).isEqualTo(milk);
        assertThat(alert.name()).isEqualTo("LEITE ITALAC 1L");
        assertThat(alert.type()).isEqualTo(Type.DEAL);
        assertThat(alert.latestPrice()).isEqualByComparingTo("4.25");
        assertThat(alert.usualPrice()).isEqualByComparingTo("5.00");
        assertThat(alert.changePercent()).isEqualByComparingTo("-15.0");
        assertThat(alert.store()).isEqualTo("CARREFOUR");
        assertThat(alert.date()).isEqualTo("2026-08-01");
    }

    @Test
    void flagsARiseWhenTheLatestPriceIsWellAboveTheUsualOne() {
        data.buy(ana, assai, "2026-07-01", milkAtAssai, "1", "5.00");
        data.buy(ana, assai, "2026-07-15", milkAtAssai, "1", "5.00");
        data.buy(ana, assai, "2026-08-01", milkAtAssai, "1", "6.00");

        Alert alert = single();

        assertThat(alert.type()).isEqualTo(Type.RISE);
        assertThat(alert.changePercent()).isEqualByComparingTo("20.0");
    }

    @Test
    void ignoresSmallDifferences() {
        data.buy(ana, assai, "2026-07-01", milkAtAssai, "1", "5.00");
        data.buy(ana, assai, "2026-07-15", milkAtAssai, "1", "5.00");
        data.buy(ana, assai, "2026-08-01", milkAtAssai, "1", "5.45");   // +9%

        assertThat(service.alerts(ana, TODAY).items()).isEmpty();
    }

    @Test
    void ignoresPricesNotSeenRecently() {
        data.buy(ana, assai, "2026-04-01", milkAtAssai, "1", "5.00");
        data.buy(ana, assai, "2026-04-15", milkAtAssai, "1", "5.00");
        data.buy(ana, assai, "2026-05-01", milkAtAssai, "1", "4.00");   // 101 days before today

        assertThat(service.alerts(ana, TODAY).items()).isEmpty();
    }

    @Test
    void needsTwoEarlierPurchasesToKnowTheUsualPrice() {
        data.buy(ana, assai, "2026-07-01", milkAtAssai, "1", "5.00");
        data.buy(ana, assai, "2026-08-01", milkAtAssai, "1", "4.00");
        data.buy(ana, carrefour, "2026-08-01", milkAtCarrefour, "1", "4.00");   // same day is not "earlier"

        assertThat(service.alerts(ana, TODAY).items()).isEmpty();
    }

    @Test
    void usualPriceOnlyUsesTheLastSixMonths() {
        data.buy(ana, assai, "2025-12-01", milkAtAssai, "1", "2.00");    // too old to count
        data.buy(ana, assai, "2026-07-01", milkAtAssai, "1", "5.00");
        data.buy(ana, assai, "2026-07-15", milkAtAssai, "1", "5.00");
        data.buy(ana, assai, "2026-08-01", milkAtAssai, "1", "5.20");

        assertThat(service.alerts(ana, TODAY).items()).isEmpty();
    }

    @Test
    void onlyReadsTheUsersOwnPurchasesAndSortsByTheBiggestChange() {
        long bia = data.user("bia@example.com");
        long coffee = data.product("CAFE PILAO 500G");
        long coffeeAtAssai = data.storeProduct(assai, coffee);
        data.buy(ana, assai, "2026-07-01", milkAtAssai, "1", "5.00");
        data.buy(ana, assai, "2026-07-15", milkAtAssai, "1", "5.00");
        data.buy(ana, assai, "2026-08-01", milkAtAssai, "1", "4.00");       // -20%
        data.buy(ana, assai, "2026-07-01", coffeeAtAssai, "1", "20.00");
        data.buy(ana, assai, "2026-07-15", coffeeAtAssai, "1", "20.00");
        data.buy(ana, assai, "2026-08-01", coffeeAtAssai, "1", "26.00");    // +30%

        assertThat(service.alerts(ana, TODAY).items()).extracting(Alert::productId).containsExactly(coffee, milk);
        assertThat(service.alerts(bia, TODAY).items()).isEmpty();
    }

    private Alert single() {
        assertThat(service.alerts(ana, TODAY).items()).hasSize(1);
        return service.alerts(ana, TODAY).items().getFirst();
    }
}
