package com.supermarketagent.insight;

import com.supermarketagent.insight.BestTimeInsight.Finding;
import com.supermarketagent.insight.BestTimeInsight.Status;
import com.supermarketagent.insight.BestTimeInsight.StoreBestTime;
import com.supermarketagent.insight.BestTimeStats.Sample;
import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowCallbackHandler;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Best part of the month and weekday to shop, store by store, only when the pattern is real. */
@Service
@Transactional(readOnly = true)
public class BestTimeInsightService {

    private static final Map<String, String> PERIOD_LABELS = Map.of(
            "DAYS_1_10", "Dias 1 a 10",
            "DAYS_11_20", "Dias 11 a 20",
            "DAYS_21_31", "Dias 21 a 31");

    // ISO day of week: 1 = Monday ... 7 = Sunday
    private static final String[] WEEKDAY_KEYS =
            {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
    private static final String[] WEEKDAY_LABELS =
            {"Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado", "Domingo"};

    // Each purchase's price against the usual price of the same product at the same store (1.0 = usual)
    private static final String RATIOS = """
            WITH items AS (
                SELECT r.store_id, COALESCE(s.display_name, s.name) AS store_name, sp.product_id, i.unit_price,
                       r.issued_at AT TIME ZONE 'America/Sao_Paulo' AS local_time
                FROM receipts r
                JOIN stores s ON s.id = r.store_id
                JOIN receipt_items i ON i.receipt_id = r.id
                JOIN store_products sp ON sp.id = i.store_product_id
                WHERE r.user_id = ? AND r.issued_at >= ? AND sp.product_id IS NOT NULL
            ),
            ratios AS (
                SELECT store_id, store_name, local_time,
                       unit_price / NULLIF(avg(unit_price) OVER (PARTITION BY store_id, product_id), 0) AS ratio,
                       count(*) OVER (PARTITION BY store_id, product_id) AS times_bought
                FROM items
            )
            """;

    private final JdbcTemplate jdbc;

    BestTimeInsightService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BestTimeInsight bestTime(long userId, Instant now, int days) {
        Timestamp from = Timestamp.from(now.minus(days, ChronoUnit.DAYS));

        Map<Long, StoreRows> stores = new LinkedHashMap<>();
        jdbc.query(RATIOS + """
                SELECT store_id, store_name,
                       CASE WHEN extract(day FROM local_time) <= 10 THEN 'DAYS_1_10'
                            WHEN extract(day FROM local_time) <= 20 THEN 'DAYS_11_20'
                            ELSE 'DAYS_21_31' END AS grp,
                       count(*) AS n, sum(ratio) AS total, sum(ratio * ratio) AS squares
                FROM ratios WHERE times_bought > 1 AND ratio IS NOT NULL
                GROUP BY 1, 2, 3 ORDER BY 2, 1, 3""",
                (RowCallbackHandler) rs -> {
                    String key = rs.getString("grp");
                    store(stores, rs).periods.add(sample(key, PERIOD_LABELS.get(key), rs));
                },
                userId, from);
        jdbc.query(RATIOS + """
                SELECT store_id, store_name, extract(isodow FROM local_time)::int AS grp,
                       count(*) AS n, sum(ratio) AS total, sum(ratio * ratio) AS squares
                FROM ratios WHERE times_bought > 1 AND ratio IS NOT NULL
                GROUP BY 1, 2, 3 ORDER BY 2, 1, 3""",
                (RowCallbackHandler) rs -> {
                    int index = rs.getInt("grp") - 1;
                    store(stores, rs).weekdays.add(sample(WEEKDAY_KEYS[index], WEEKDAY_LABELS[index], rs));
                },
                userId, from);

        List<StoreBestTime> result = stores.entrySet().stream()
                .map(entry -> new StoreBestTime(
                        entry.getKey(),
                        entry.getValue().name,
                        (int) entry.getValue().periods.stream().mapToLong(Sample::count).sum(),
                        BestTimeStats.decide(entry.getValue().periods),
                        BestTimeStats.decide(entry.getValue().weekdays)))
                .sorted(Comparator.comparing(BestTimeInsightService::strongestPattern).reversed()
                        .thenComparing(Comparator.comparingInt(StoreBestTime::comparablePurchases).reversed()))
                .toList();
        return new BestTimeInsight(days, result);
    }

    /** Biggest saving found at the store, 0 without a pattern; used to show useful stores first. */
    static BigDecimal strongestPattern(StoreBestTime store) {
        return List.of(store.periodOfMonth(), store.weekday()).stream()
                .filter(finding -> finding.status() == Status.PATTERN)
                .map(Finding::percentCheaper)
                .max(Comparator.naturalOrder())
                .orElse(BigDecimal.ZERO);
    }

    private static StoreRows store(Map<Long, StoreRows> stores, ResultSet rs) throws SQLException {
        String name = rs.getString("store_name");
        return stores.computeIfAbsent(rs.getLong("store_id"), id -> new StoreRows(name));
    }

    private static Sample sample(String key, String label, ResultSet rs) throws SQLException {
        return new Sample(key, label, rs.getLong("n"), rs.getDouble("total"), rs.getDouble("squares"));
    }

    private static final class StoreRows {
        private final String name;
        private final List<Sample> periods = new ArrayList<>();
        private final List<Sample> weekdays = new ArrayList<>();

        private StoreRows(String name) {
            this.name = name;
        }
    }
}
