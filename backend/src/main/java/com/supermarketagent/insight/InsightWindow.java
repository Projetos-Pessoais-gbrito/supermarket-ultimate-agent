package com.supermarketagent.insight;

import java.sql.Timestamp;
import java.time.YearMonth;
import java.time.temporal.ChronoUnit;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Resolves the dashboard period. {@code months = 0} means "Tudo": from the month of the user's first
 * receipt, so imported old receipts are always included.
 */
@Component
public class InsightWindow {

    public static final int ALL = 0;
    /** Upper bound for "Tudo", to keep charts and queries bounded. */
    static final int MAX_MONTHS = 120;

    private final JdbcTemplate jdbc;

    InsightWindow(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Number of months (including the current one) the period covers. */
    public int months(long userId, YearMonth currentMonth, int requestedMonths) {
        if (requestedMonths != ALL) {
            return requestedMonths;
        }
        Timestamp first = jdbc.queryForObject("SELECT min(issued_at) FROM receipts WHERE user_id = ?",
                Timestamp.class, userId);
        if (first == null) {
            return 1;
        }
        YearMonth firstMonth = YearMonth.from(first.toInstant().atZone(InsightPeriod.SAO_PAULO));
        long months = ChronoUnit.MONTHS.between(firstMonth.atDay(1), currentMonth.atDay(1)) + 1;
        return (int) Math.clamp(months, 1, MAX_MONTHS);
    }
}
