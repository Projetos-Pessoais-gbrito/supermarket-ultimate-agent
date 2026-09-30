package com.supermarketagent.insight;

import static com.supermarketagent.insight.InsightPeriod.endOf;
import static com.supermarketagent.insight.InsightPeriod.startOf;

import com.supermarketagent.catalog.ProductCategory;
import com.supermarketagent.insight.SpendingInsight.CategoryTotal;
import com.supermarketagent.insight.SpendingInsight.MonthTotal;
import com.supermarketagent.insight.SpendingInsight.StoreTotal;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** How much the user spends, per month, store and category. Numbers come from SQL, never from AI. */
@Service
@Transactional(readOnly = true)
public class SpendingInsightService {

    static final String UNCATEGORIZED_LABEL = "Sem categoria";

    private final JdbcTemplate jdbc;
    private final InsightWindow window;

    SpendingInsightService(JdbcTemplate jdbc, InsightWindow window) {
        this.jdbc = jdbc;
        this.window = window;
    }

    /** Whole-month view (e.g. past months or tests): the comparison covers the full previous month. */
    public SpendingInsight spending(long userId, YearMonth currentMonth, int requestedMonths) {
        return spending(userId, currentMonth.atEndOfMonth(), requestedMonths);
    }

    /**
     * @param today           the day considered "now" (São Paulo time); its month is the current month
     * @param requestedMonths window size, including the current month; {@link InsightWindow#ALL} for everything
     */
    public SpendingInsight spending(long userId, LocalDate today, int requestedMonths) {
        YearMonth currentMonth = YearMonth.from(today);
        int months = window.months(userId, currentMonth, requestedMonths);
        YearMonth firstMonth = currentMonth.minusMonths(months - 1L);
        Timestamp from = Timestamp.from(startOf(firstMonth));
        Timestamp to = Timestamp.from(endOf(currentMonth));

        List<MonthTotal> monthly = monthly(userId, firstMonth, currentMonth, from, to);
        MonthTotal current = monthly.getLast();
        YearMonth previousMonth = currentMonth.minusMonths(1);
        MonthTotal previous = months > 1 ? monthly.get(monthly.size() - 2) : monthTotal(userId, previousMonth);

        // A month in progress is compared with the same days of the previous month (day 1 to today)
        int untilDay = Math.min(today.getDayOfMonth(), previousMonth.lengthOfMonth());
        MonthTotal previousToDate = untilDay == previousMonth.lengthOfMonth()
                ? previous
                : total(userId, previousMonth.toString(), Timestamp.from(startOf(previousMonth)),
                        Timestamp.from(previousMonth.atDay(untilDay + 1).atStartOfDay(InsightPeriod.SAO_PAULO)
                                .toInstant()));

        return new SpendingInsight(current, previous, previousToDate, untilDay,
                changePercent(current.total(), previousToDate.total()), monthly,
                byStore(userId, from, to), byCategory(userId, from, to));
    }

    private MonthTotal total(long userId, String month, Timestamp from, Timestamp to) {
        return jdbc.queryForObject("""
                SELECT coalesce(sum(total_amount), 0) AS total, count(*) AS receipts
                FROM receipts
                WHERE user_id = ? AND issued_at >= ? AND issued_at < ?""",
                (rs, row) -> new MonthTotal(month, rs.getBigDecimal("total"), rs.getInt("receipts")),
                userId, from, to);
    }

    private List<MonthTotal> monthly(long userId, YearMonth first, YearMonth last, Timestamp from, Timestamp to) {
        Map<String, MonthTotal> found = jdbc.query("""
                SELECT to_char(issued_at AT TIME ZONE 'America/Sao_Paulo', 'YYYY-MM') AS month,
                       sum(total_amount) AS total, count(*) AS receipts
                FROM receipts
                WHERE user_id = ? AND issued_at >= ? AND issued_at < ?
                GROUP BY 1""",
                (rs, row) -> new MonthTotal(rs.getString("month"), rs.getBigDecimal("total"), rs.getInt("receipts")),
                userId, from, to)
                .stream().collect(Collectors.toMap(MonthTotal::month, Function.identity()));

        List<MonthTotal> monthly = new ArrayList<>();
        for (YearMonth month = first; !month.isAfter(last); month = month.plusMonths(1)) {
            monthly.add(found.getOrDefault(month.toString(), new MonthTotal(month.toString(), BigDecimal.ZERO, 0)));
        }
        return monthly;
    }

    private MonthTotal monthTotal(long userId, YearMonth month) {
        return monthly(userId, month, month, Timestamp.from(startOf(month)), Timestamp.from(endOf(month))).getFirst();
    }

    private List<StoreTotal> byStore(long userId, Timestamp from, Timestamp to) {
        return jdbc.query("""
                SELECT min(s.id) AS id, COALESCE(s.display_name, s.name) AS name,
                       sum(r.total_amount) AS total, count(*) AS receipts
                FROM receipts r JOIN stores s ON s.id = r.store_id
                WHERE r.user_id = ? AND r.issued_at >= ? AND r.issued_at < ?
                -- Branches of the same chain (different CNPJs) count as one store
                GROUP BY COALESCE(s.display_name, s.name)
                ORDER BY total DESC, name""",
                (rs, row) -> new StoreTotal(rs.getLong("id"), rs.getString("name"), rs.getBigDecimal("total"),
                        rs.getInt("receipts")),
                userId, from, to);
    }

    private List<CategoryTotal> byCategory(long userId, Timestamp from, Timestamp to) {
        return jdbc.query("""
                SELECT p.category, sum(i.total_price) AS total
                FROM receipts r
                JOIN receipt_items i ON i.receipt_id = r.id
                JOIN store_products sp ON sp.id = i.store_product_id
                LEFT JOIN products p ON p.id = sp.product_id
                WHERE r.user_id = ? AND r.issued_at >= ? AND r.issued_at < ?
                GROUP BY p.category
                ORDER BY total DESC""",
                (rs, row) -> category(rs.getString("category"), rs.getBigDecimal("total")),
                userId, from, to);
    }

    private static CategoryTotal category(String category, BigDecimal total) {
        String label = category == null ? UNCATEGORIZED_LABEL : ProductCategory.valueOf(category).label();
        return new CategoryTotal(category, label, total);
    }

    static BigDecimal changePercent(BigDecimal current, BigDecimal previous) {
        if (previous.signum() == 0) {
            return null;
        }
        return current.subtract(previous).multiply(BigDecimal.valueOf(100)).divide(previous, 1, RoundingMode.HALF_UP);
    }
}
