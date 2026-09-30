package com.supermarketagent.insight;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.supermarketagent.TestcontainersConfiguration;
import com.supermarketagent.ai.AiClient;
import com.supermarketagent.ai.AiUnavailableException;
import com.supermarketagent.insight.InsightSummaryService.Answer;
import java.time.Instant;
import java.time.LocalDate;
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
class InsightSummaryServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 30);
    private static final Instant NOW = Instant.parse("2026-09-30T12:00:00Z");

    @MockitoBean
    private AiClient ai;

    @Autowired
    private InsightSummaryService service;

    @Autowired
    private JdbcTemplate jdbc;

    private InsightTestData data;
    private long ana;
    private long assai;
    private long carrefour;
    private long riceAtAssai;
    private long riceAtCarrefour;

    @BeforeEach
    void setUp() {
        data = new InsightTestData(jdbc);
        data.reset();
        ana = data.user("ana@example.com");
        assai = data.store("ASSAI");
        carrefour = data.store("CARREFOUR");
        long rice = data.product("ARROZ TIO JOAO TP1", "MERCEARIA");
        riceAtAssai = data.storeProduct(assai, rice, "ARROZ TIO JOAO TP1 5KG");
        riceAtCarrefour = data.storeProduct(carrefour, rice, "ARROZ T.JOAO TP1 5KG");
        when(ai.isAvailable()).thenReturn(true);
        when(ai.generateJson(any(), anyMap(), eq(Answer.class)))
                .thenReturn(new Answer(List.of("Compre arroz no ASSAI: sai mais barato.")));
    }

    @Test
    void sendsComputedFactsAndReturnsTheTips() {
        data.receipt(ana, assai, "2026-08-10T15:00:00Z", riceAtAssai, "4", "25.00");
        data.receipt(ana, carrefour, "2026-09-10T15:00:00Z", riceAtCarrefour, "2", "30.00");

        InsightSummary summary = service.summary(ana, TODAY, NOW);

        assertThat(summary.available()).isTrue();
        assertThat(summary.tips()).containsExactly("Compre arroz no ASSAI: sai mais barato.");
        verify(ai).generateJson(argThat(prompt -> prompt.contains("Gasto em setembro: R$ 60,00 em 1 notas.")
                && prompt.contains("Variação em relação a agosto no mesmo período (dias 1 a 30): -40,0%.")
                && prompt.contains("melhor preço R$ 25,00 no ASSAI")
                && prompt.contains("SOMENTE os fatos")
                && prompt.contains("circulares")
                && !prompt.contains("Mercado onde mais gastou")), anyMap(), eq(Answer.class));
    }

    @Test
    void reusesTipsWhileTheDataDoesNotChange() {
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", riceAtAssai, "1", "25.00");

        service.summary(ana, TODAY, NOW);
        service.summary(ana, TODAY, NOW);
        verify(ai, times(1)).generateJson(any(), anyMap(), eq(Answer.class));

        data.receipt(ana, assai, "2026-09-12T15:00:00Z", riceAtAssai, "1", "26.00");
        service.summary(ana, TODAY, NOW);
        verify(ai, times(2)).generateJson(any(), anyMap(), eq(Answer.class));
    }

    @Test
    void keepsTipsInTheDatabaseSoTheySurviveRestarts() {
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", riceAtAssai, "1", "25.00");

        service.summary(ana, TODAY, NOW);

        assertThat(jdbc.queryForObject("SELECT tips::text FROM insight_summaries WHERE user_id = ?", String.class, ana))
                .contains("Compre arroz no ASSAI");
    }

    @Test
    void keepsOnlyTheLatestSummaryPerUser() {
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", riceAtAssai, "1", "25.00");
        service.summary(ana, TODAY, NOW);
        data.receipt(ana, assai, "2026-09-12T15:00:00Z", riceAtAssai, "1", "26.00");
        service.summary(ana, TODAY, NOW);

        assertThat(jdbc.queryForObject("SELECT count(*) FROM insight_summaries WHERE user_id = ?", Integer.class, ana))
                .isEqualTo(1);
    }

    @Test
    void doesNotCallTheAiWithoutPurchases() {
        InsightSummary summary = service.summary(ana, TODAY, NOW);

        assertThat(summary.available()).isTrue();
        assertThat(summary.tips()).isEmpty();
        verify(ai, never()).generateJson(any(), anyMap(), any());
    }

    @Test
    void reportsUnavailableWhenTheAiFails() {
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", riceAtAssai, "1", "25.00");
        when(ai.generateJson(any(), anyMap(), eq(Answer.class))).thenThrow(new AiUnavailableException("quota"));

        assertThat(service.summary(ana, TODAY, NOW)).isEqualTo(new InsightSummary(false, List.of()));
    }

    @Test
    void keepsAtMostThreeShortTips() {
        data.receipt(ana, assai, "2026-09-10T15:00:00Z", riceAtAssai, "1", "25.00");
        when(ai.generateJson(any(), anyMap(), eq(Answer.class)))
                .thenReturn(new Answer(List.of("a", " ", "b", "c", "d", "x".repeat(500))));

        List<String> tips = service.summary(ana, TODAY, NOW).tips();

        assertThat(tips).containsExactly("a", "b", "c");
    }

    @Test
    void isUnavailableWithoutAnAiProvider() {
        when(ai.isAvailable()).thenReturn(false);

        assertThat(service.summary(ana, TODAY, NOW).available()).isFalse();
    }
}
