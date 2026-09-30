package com.supermarketagent.insight;

import static com.supermarketagent.insight.InsightPeriod.endOf;
import static com.supermarketagent.insight.InsightPeriod.startOf;

import com.supermarketagent.insight.InflationInsight.MonthChange;
import com.supermarketagent.insight.InflationInsight.ProductChange;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Month-over-month price change of the user's basket: products bought in both months, each
 * weighted by what the user spent on it in the earlier month (Laspeyres-style, like the IPCA).
 */
@Service
@Transactional(readOnly = true)
public class InflationInsightService {

    // Average price and spending per product and month (São Paulo time)
    private static final String MONTHLY_PRICES = """
            WITH monthly AS (
                SELECT sp.product_id,
                       to_char(r.issued_at AT TIME ZONE 'America/Sao_Paulo', 'YYYY-MM') AS month,
                       avg(i.unit_price) AS avg_price, sum(i.total_price) AS spent
                FROM receipts r
                JOIN receipt_items i ON i.receipt_id = r.id
                JOIN store_products sp ON sp.id = i.store_product_id
                WHERE r.user_id = ? AND r.issued_at >= ? AND r.issued_at < ? AND sp.product_id IS NOT NULL
                GROUP BY 1, 2
            ),
            pairs AS (
                SELECT cur.product_id, cur.month, prev.avg_price AS previous_price, cur.avg_price AS current_price,
                       prev.spent AS weight
                FROM monthly cur
                JOIN monthly prev ON prev.product_id = cur.product_id
                 AND prev.month = to_char(to_date(cur.month, 'YYYY-MM') - interval '1 month', 'YYYY-MM')
                WHERE prev.avg_price > 0
            )
            """;

    private final JdbcTemplate jdbc;

    InflationInsightService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public InflationInsight inflation(long userId, YearMonth currentMonth, int months) {
        YearMonth firstMonth = currentMonth.minusMonths(months - 1L);
        // One extra month back, so the first month of the window has something to compare with
        Timestamp from = Timestamp.from(startOf(firstMonth.minusMonths(1)));
        Timestamp to = Timestamp.from(endOf(currentMonth));

        Map<String, MonthChange> found = jdbc.query(MONTHLY_PRICES + """
                SELECT month, sum(weight * current_price / previous_price) / sum(weight) AS ratio, count(*) AS products
                FROM pairs GROUP BY month""",
                (rs, row) -> new MonthChange(rs.getString("month"), percent(rs.getBigDecimal("ratio")),
                        rs.getInt("products")),
                userId, from, to)
                .stream().collect(Collectors.toMap(MonthChange::month, Function.identity()));

        List<MonthChange> monthly = new ArrayList<>();
        for (YearMonth month = firstMonth; !month.isAfter(currentMonth); month = month.plusMonths(1)) {
            monthly.add(found.getOrDefault(month.toString(), new MonthChange(month.toString(), null, 0)));
        }

        List<ProductChange> changes = jdbc.query(MONTHLY_PRICES + """
                SELECT pairs.product_id, COALESCE(p.display_name, p.normalized_name) AS name, previous_price, current_price,
                       current_price / previous_price AS ratio
                FROM pairs JOIN products p ON p.id = pairs.product_id
                WHERE month = ?
                ORDER BY ratio DESC, name""",
                (rs, row) -> new ProductChange(rs.getLong("product_id"), rs.getString("name"),
                        money(rs.getBigDecimal("previous_price")), money(rs.getBigDecimal("current_price")),
                        percent(rs.getBigDecimal("ratio"))),
                userId, from, to, currentMonth.toString());

        return new InflationInsight(monthly, changes);
    }

    private static BigDecimal percent(BigDecimal ratio) {
        return ratio.subtract(BigDecimal.ONE).multiply(BigDecimal.valueOf(100)).setScale(1, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
