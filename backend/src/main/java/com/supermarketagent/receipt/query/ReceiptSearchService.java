package com.supermarketagent.receipt.query;

import static com.supermarketagent.insight.InsightPeriod.endOf;
import static com.supermarketagent.insight.InsightPeriod.startOf;

import com.supermarketagent.receipt.query.ReceiptSearch.Filter;
import com.supermarketagent.receipt.query.ReceiptSearch.MonthlyTotal;
import com.supermarketagent.receipt.query.ReceiptSearch.Result;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Owner-scoped receipt listing with store, month and product-text filters. */
@Service
@Transactional(readOnly = true)
public class ReceiptSearchService {

    /** Month of a receipt in São Paulo time, as {@code YYYY-MM}. */
    private static final String MONTH = "to_char(r.issued_at AT TIME ZONE 'America/Sao_Paulo', 'YYYY-MM')";
    private static final String STORE_NAME = "COALESCE(s.display_name, s.name)";

    private final JdbcTemplate jdbc;

    ReceiptSearchService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Result search(long userId, Filter filter, int page, int size) {
        List<Object> args = new ArrayList<>();
        String where = where(userId, filter, args);
        String from = " FROM receipts r JOIN stores s ON s.id = r.store_id WHERE " + where;

        List<ReceiptSummary> content = jdbc.query("SELECT r.id, " + STORE_NAME + " AS store_name, r.issued_at,"
                        + " r.total_amount, (SELECT count(*) FROM receipt_items i WHERE i.receipt_id = r.id) AS item_count"
                        + from + " ORDER BY r.issued_at DESC, r.id DESC LIMIT ? OFFSET ?",
                (rs, row) -> new ReceiptSummary(rs.getLong("id"), rs.getString("store_name"),
                        rs.getTimestamp("issued_at").toInstant(), rs.getBigDecimal("total_amount"),
                        rs.getInt("item_count")),
                append(args, size, (long) page * size));

        List<MonthlyTotal> monthlyTotals = jdbc.query("SELECT " + MONTH + " AS month, sum(r.total_amount) AS total,"
                        + " count(*) AS receipt_count" + from + " GROUP BY 1 ORDER BY 1 DESC",
                (rs, row) -> new MonthlyTotal(rs.getString("month"), rs.getBigDecimal("total"),
                        rs.getLong("receipt_count")),
                args.toArray());
        long total = monthlyTotals.stream().mapToLong(MonthlyTotal::receiptCount).sum();

        // Chip options come from all of the user's receipts, so a filter never hides the others
        List<String> stores = jdbc.queryForList("SELECT DISTINCT " + STORE_NAME + " AS name FROM receipts r"
                + " JOIN stores s ON s.id = r.store_id WHERE r.user_id = ? ORDER BY 1", String.class, userId);
        List<String> months = jdbc.queryForList("SELECT DISTINCT " + MONTH + " FROM receipts r"
                + " WHERE r.user_id = ? ORDER BY 1 DESC", String.class, userId);

        return new Result(content, page, size, total, (int) ((total + size - 1) / size), monthlyTotals, stores,
                months);
    }

    private static String where(long userId, Filter filter, List<Object> args) {
        StringBuilder where = new StringBuilder("r.user_id = ?");
        args.add(userId);
        if (filter.store() != null && !filter.store().isBlank()) {
            where.append(" AND ").append(STORE_NAME).append(" = ?");
            args.add(filter.store().strip());
        }
        if (filter.month() != null) {
            where.append(" AND r.issued_at >= ? AND r.issued_at < ?");
            args.add(Timestamp.from(startOf(filter.month())));
            args.add(Timestamp.from(endOf(filter.month())));
        }
        if (filter.text() != null && !filter.text().isBlank()) {
            where.append(" AND ").append("""
                     EXISTS (SELECT 1 FROM receipt_items i
                        JOIN store_products sp ON sp.id = i.store_product_id
                        LEFT JOIN products p ON p.id = sp.product_id
                        WHERE i.receipt_id = r.id
                          AND (unaccent(lower(sp.description)) LIKE unaccent(lower(?)) ESCAPE '\\'
                            OR unaccent(lower(COALESCE(p.display_name, p.normalized_name, ''))) LIKE unaccent(lower(?)) ESCAPE '\\'))""");
            String pattern = "%" + escapeLike(filter.text().strip()) + "%";
            args.add(pattern);
            args.add(pattern);
        }
        return where.toString();
    }

    static String escapeLike(String text) {
        return text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static Object[] append(List<Object> args, Object... more) {
        List<Object> all = new ArrayList<>(args);
        all.addAll(List.of(more));
        return all.toArray();
    }
}
