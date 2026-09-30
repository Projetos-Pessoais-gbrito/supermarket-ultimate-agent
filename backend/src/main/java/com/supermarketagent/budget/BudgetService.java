package com.supermarketagent.budget;

import static com.supermarketagent.insight.InsightPeriod.endOf;
import static com.supermarketagent.insight.InsightPeriod.startOf;

import com.supermarketagent.budget.BudgetSettings.CategoryLimit;
import com.supermarketagent.budget.BudgetStatus.Line;
import com.supermarketagent.budget.BudgetStatus.State;
import com.supermarketagent.catalog.ProductCategory;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Monthly budgets: the user's limits and how this month's spending compares to them. */
@Service
public class BudgetService {

    /** From this share of the limit on, the budget is flagged as a warning. */
    static final BigDecimal WARNING_PERCENT = BigDecimal.valueOf(80);
    static final BigDecimal MAX_LIMIT = new BigDecimal("1000000");
    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final JdbcTemplate jdbc;

    BudgetService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Transactional(readOnly = true)
    public BudgetSettings settings(long userId) {
        BigDecimal overall = null;
        List<CategoryLimit> categories = new ArrayList<>();
        for (Map<String, Object> row : jdbc.queryForList(
                "SELECT category, monthly_limit FROM budgets WHERE user_id = ? ORDER BY category NULLS FIRST", userId)) {
            String category = (String) row.get("category");
            BigDecimal limit = (BigDecimal) row.get("monthly_limit");
            if (category == null) {
                overall = limit;
            } else {
                categories.add(new CategoryLimit(category, ProductCategory.valueOf(category).label(), limit));
            }
        }
        return new BudgetSettings(overall, categories);
    }

    /** Replaces all of the user's limits with {@code settings}. */
    @Transactional
    public BudgetSettings replace(long userId, BudgetSettings settings) {
        validate(settings);
        jdbc.update("DELETE FROM budgets WHERE user_id = ?", userId);
        if (settings.overall() != null) {
            insert(userId, null, settings.overall());
        }
        for (CategoryLimit limit : settings.categories()) {
            insert(userId, limit.category(), limit.limit());
        }
        return settings(userId);
    }

    /** Spending of {@code today}'s month (São Paulo) against each limit, with a month-end projection. */
    @Transactional(readOnly = true)
    public BudgetStatus status(long userId, LocalDate today) {
        YearMonth month = YearMonth.from(today);
        int daysElapsed = today.getDayOfMonth();
        int daysInMonth = month.lengthOfMonth();
        Timestamp from = Timestamp.from(startOf(month));
        Timestamp to = Timestamp.from(endOf(month));
        BudgetSettings settings = settings(userId);

        Line overall = null;
        if (settings.overall() != null) {
            BigDecimal spent = jdbc.queryForObject("""
                    SELECT COALESCE(sum(total_amount), 0) FROM receipts
                    WHERE user_id = ? AND issued_at >= ? AND issued_at < ?""", BigDecimal.class, userId, from, to);
            overall = line(null, "Total do mês", settings.overall(), spent, daysElapsed, daysInMonth);
        }

        Map<String, BigDecimal> spentByCategory = jdbc.query("""
                SELECT p.category, sum(i.total_price) AS spent
                FROM receipts r
                JOIN receipt_items i ON i.receipt_id = r.id
                JOIN store_products sp ON sp.id = i.store_product_id
                JOIN products p ON p.id = sp.product_id
                WHERE r.user_id = ? AND r.issued_at >= ? AND r.issued_at < ? AND p.category IS NOT NULL
                GROUP BY p.category""",
                (rs, row) -> Map.entry(rs.getString("category"), rs.getBigDecimal("spent")), userId, from, to)
                .stream().collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));

        List<Line> categories = settings.categories().stream()
                .map(limit -> line(limit.category(), limit.label(), limit.limit(),
                        spentByCategory.getOrDefault(limit.category(), BigDecimal.ZERO), daysElapsed, daysInMonth))
                .sorted(Comparator.comparing(Line::percentUsed).reversed())
                .toList();
        return new BudgetStatus(month.toString(), daysElapsed, daysInMonth, overall, categories);
    }

    static Line line(String category, String label, BigDecimal limit, BigDecimal spent, int daysElapsed,
                     int daysInMonth) {
        BigDecimal percent = spent.multiply(HUNDRED).divide(limit, 1, RoundingMode.HALF_UP);
        // Straight-line projection of the current pace to the end of the month
        BigDecimal projected = spent.multiply(BigDecimal.valueOf(daysInMonth))
                .divide(BigDecimal.valueOf(daysElapsed), 2, RoundingMode.HALF_UP);
        State state = spent.compareTo(limit) > 0 ? State.OVER
                : percent.compareTo(WARNING_PERCENT) >= 0 ? State.WARNING
                : State.OK;
        return new Line(category, label, limit, spent.setScale(2, RoundingMode.HALF_UP), percent, projected,
                projected.compareTo(limit) > 0, state);
    }

    private static void validate(BudgetSettings settings) {
        if (settings.overall() != null) {
            checkLimit(settings.overall());
        }
        Set<String> seen = new HashSet<>();
        for (CategoryLimit limit : settings.categories()) {
            if (limit.category() == null || !isKnownCategory(limit.category())) {
                throw new InvalidBudgetException("Unknown category: " + limit.category());
            }
            if (!seen.add(limit.category())) {
                throw new InvalidBudgetException("Category listed twice: " + limit.category());
            }
            checkLimit(limit.limit());
        }
    }

    private static void checkLimit(BigDecimal limit) {
        if (limit == null || limit.signum() <= 0 || limit.compareTo(MAX_LIMIT) > 0 || limit.scale() > 2) {
            throw new InvalidBudgetException("Limits must be positive amounts up to 1,000,000.00 with 2 decimals");
        }
    }

    private static boolean isKnownCategory(String category) {
        for (ProductCategory known : ProductCategory.values()) {
            if (known.name().equals(category)) {
                return true;
            }
        }
        return false;
    }

    private void insert(long userId, String category, BigDecimal limit) {
        jdbc.update("INSERT INTO budgets (user_id, category, monthly_limit) VALUES (?, ?, ?)", userId, category, limit);
    }
}
