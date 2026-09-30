package com.supermarketagent.insight;

import com.supermarketagent.insight.BestDayInsight.Group;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Best period of the month and day of the week to buy, from the user's own price history. */
@Service
@Transactional(readOnly = true)
public class BestDayInsightService {

    /** Below this, a group is too small to recommend. */
    static final int MIN_SAMPLES_PER_GROUP = 3;

    private static final Map<String, String> PERIOD_LABELS = Map.of(
            "DAYS_1_10", "Dias 1 a 10",
            "DAYS_11_20", "Dias 11 a 20",
            "DAYS_21_31", "Dias 21 a 31");

    // ISO day of week: 1 = Monday ... 7 = Sunday
    private static final String[] WEEKDAY_KEYS =
            {"MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY", "SATURDAY", "SUNDAY"};
    private static final String[] WEEKDAY_LABELS =
            {"Segunda-feira", "Terça-feira", "Quarta-feira", "Quinta-feira", "Sexta-feira", "Sábado", "Domingo"};

    // Each purchase's price relative to the average price of the same product (1.0 = average)
    private static final String RATIOS = """
            WITH items AS (
                SELECT sp.product_id, i.unit_price, r.issued_at AT TIME ZONE 'America/Sao_Paulo' AS local_time
                FROM receipts r
                JOIN receipt_items i ON i.receipt_id = r.id
                JOIN store_products sp ON sp.id = i.store_product_id
                WHERE r.user_id = ? AND r.issued_at >= ? AND sp.product_id IS NOT NULL
            ),
            ratios AS (
                SELECT local_time,
                       unit_price / NULLIF(avg(unit_price) OVER (PARTITION BY product_id), 0) AS ratio,
                       count(*) OVER (PARTITION BY product_id) AS times_bought
                FROM items
            )
            """;

    private final JdbcTemplate jdbc;

    BestDayInsightService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public BestDayInsight bestDay(long userId, Instant now, int days) {
        Timestamp from = Timestamp.from(now.minus(days, ChronoUnit.DAYS));

        List<Group> periods = jdbc.query(RATIOS + """
                SELECT CASE WHEN extract(day FROM local_time) <= 10 THEN 'DAYS_1_10'
                            WHEN extract(day FROM local_time) <= 20 THEN 'DAYS_11_20'
                            ELSE 'DAYS_21_31' END AS period,
                       avg(ratio) AS avg_ratio, count(*) AS samples
                FROM ratios WHERE times_bought > 1
                GROUP BY 1 ORDER BY 1""",
                (rs, row) -> group(rs.getString("period"), PERIOD_LABELS.get(rs.getString("period")),
                        rs.getBigDecimal("avg_ratio"), rs.getInt("samples")),
                userId, from);

        List<Group> weekdays = jdbc.query(RATIOS + """
                SELECT extract(isodow FROM local_time)::int AS weekday, avg(ratio) AS avg_ratio, count(*) AS samples
                FROM ratios WHERE times_bought > 1
                GROUP BY 1 ORDER BY 1""",
                (rs, row) -> {
                    int index = rs.getInt("weekday") - 1;
                    return group(WEEKDAY_KEYS[index], WEEKDAY_LABELS[index], rs.getBigDecimal("avg_ratio"),
                            rs.getInt("samples"));
                },
                userId, from);

        int comparableItems = periods.stream().mapToInt(Group::samples).sum();
        return new BestDayInsight(comparableItems, best(periods), periods, best(weekdays), weekdays);
    }

    /** Cheapest group, only when at least two groups have enough purchases to compare. */
    static Group best(List<Group> groups) {
        List<Group> reliable = groups.stream().filter(g -> g.samples() >= MIN_SAMPLES_PER_GROUP).toList();
        if (reliable.size() < 2) {
            return null;
        }
        return reliable.stream().min(Comparator.comparing(Group::percentVsAverage)).orElseThrow();
    }

    private static Group group(String key, String label, BigDecimal avgRatio, int samples) {
        BigDecimal percent = avgRatio.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100))
                .setScale(1, RoundingMode.HALF_UP);
        return new Group(key, label, percent, samples);
    }
}
