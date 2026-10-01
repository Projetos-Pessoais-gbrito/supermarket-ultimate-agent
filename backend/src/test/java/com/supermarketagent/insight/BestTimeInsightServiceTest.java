package com.supermarketagent.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.insight.BestTimeInsight.Status;
import com.supermarketagent.insight.BestTimeInsight.StoreBestTime;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class BestTimeInsightServiceTest {

    private static final Instant NOW = Instant.parse("2026-10-01T12:00:00Z");
    private static final String[] MONTHS = {"2026-07", "2026-08", "2026-09"};

    @Autowired
    private BestTimeInsightService service;

    @Autowired
    private JdbcTemplate jdbc;

    @Autowired
    private MockMvc mvc;

    private InsightTestData data;
    private long ana;
    private long assai;
    private long carrefour;

    @BeforeEach
    void setUp() {
        data = new InsightTestData(jdbc);
        data.reset();
        ana = data.user("ana@example.com");
        assai = data.store("ASSAI");
        carrefour = data.store("CARREFOUR");
    }

    @Test
    void findsTheCheaperPartOfTheMonthAtAStore() {
        List<Long> products = storeProducts(assai, 3);
        for (String month : MONTHS) {
            for (long product : products) {
                data.receipt(ana, assai, month + "-05T15:00:00Z", product, "1", "9.00");  // early: cheaper
                data.receipt(ana, assai, month + "-15T15:00:00Z", product, "1", "10.00");
                data.receipt(ana, assai, month + "-25T15:00:00Z", product, "1", "10.00");
            }
        }

        StoreBestTime store = service.bestTime(ana, NOW, 365).stores().getFirst();

        assertThat(store.storeName()).isEqualTo("ASSAI");
        assertThat(store.comparablePurchases()).isEqualTo(27);
        assertThat(store.periodOfMonth().status()).isEqualTo(Status.PATTERN);
        assertThat(store.periodOfMonth().best().key()).isEqualTo("DAYS_1_10");
        assertThat(store.periodOfMonth().percentCheaper()).isEqualByComparingTo("10.0");
    }

    @Test
    void shoppingAtACheaperStoreEarlyInTheMonthIsNotATimingPattern() {
        // Assaí is always cheaper, and happens to be visited early in the month: per store, prices
        // do not depend on the day, so neither store has a pattern
        List<Long> atAssai = storeProducts(assai, 3);
        List<Long> atCarrefour = storeProducts(carrefour, 3);
        for (String month : MONTHS) {
            for (int i = 0; i < 3; i++) {
                data.receipt(ana, assai, month + "-03T15:00:00Z", atAssai.get(i), "1", "9.00");
                data.receipt(ana, assai, month + "-13T15:00:00Z", atAssai.get(i), "1", "9.00");
                data.receipt(ana, carrefour, month + "-14T15:00:00Z", atCarrefour.get(i), "1", "10.00");
                data.receipt(ana, carrefour, month + "-24T15:00:00Z", atCarrefour.get(i), "1", "10.00");
            }
        }

        List<StoreBestTime> stores = service.bestTime(ana, NOW, 365).stores();

        assertThat(stores).hasSize(2);
        assertThat(stores).allSatisfy(store -> assertThat(store.periodOfMonth().status()).isEqualTo(Status.NO_PATTERN));
    }

    @Test
    void reportsTooFewPurchases() {
        long product = storeProducts(assai, 1).getFirst();
        data.receipt(ana, assai, "2026-09-05T15:00:00Z", product, "1", "9.00");
        data.receipt(ana, assai, "2026-09-15T15:00:00Z", product, "1", "10.00");

        StoreBestTime store = service.bestTime(ana, NOW, 365).stores().getFirst();

        assertThat(store.periodOfMonth().status()).isEqualTo(Status.NOT_ENOUGH_DATA);
        assertThat(store.weekday().status()).isEqualTo(Status.NOT_ENOUGH_DATA);
    }

    @Test
    void ignoresOtherUsers() {
        long bia = data.user("bia@example.com");
        long product = storeProducts(assai, 1).getFirst();
        data.receipt(bia, assai, "2026-09-05T15:00:00Z", product, "1", "9.00");
        data.receipt(bia, assai, "2026-09-15T15:00:00Z", product, "1", "10.00");

        assertThat(service.bestTime(ana, NOW, 365).stores()).isEmpty();
    }

    @Test
    void endpointRequiresAuthentication() throws Exception {
        mvc.perform(get("/api/insights/best-time")).andExpect(status().isUnauthorized());
    }

    private List<Long> storeProducts(long store, int count) {
        List<Long> ids = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            long product = data.product("PRODUTO " + store + "-" + i, "MERCEARIA");
            ids.add(data.storeProduct(store, product, "PRODUTO " + store + "-" + i));
        }
        return ids;
    }
}
