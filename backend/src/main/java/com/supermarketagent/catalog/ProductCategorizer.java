package com.supermarketagent.catalog;

import com.supermarketagent.ai.AiClient;
import com.supermarketagent.ai.AiUnavailableException;
import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

/**
 * Assigns a {@link ProductCategory} to uncategorized products with the AI client.
 *
 * <p>Only product names and sizes are sent, never user data. No transaction is held during the AI
 * call: products are read, the model answers, then each category is written only if still empty.
 */
@Service
public class ProductCategorizer {

    static final int BATCH_SIZE = 50;

    private static final Logger log = LoggerFactory.getLogger(ProductCategorizer.class);

    private final AiClient ai;
    private final JdbcTemplate jdbc;

    ProductCategorizer(AiClient ai, JdbcTemplate jdbc) {
        this.ai = ai;
        this.jdbc = jdbc;
    }

    /** Categorizes one batch; returns how many products received a category. */
    public int categorizePending() {
        if (!ai.isAvailable()) {
            return 0;
        }
        List<PendingProduct> pending = jdbc.query("""
                SELECT id, normalized_name, measure_value, measure_unit FROM products
                WHERE category IS NULL ORDER BY id LIMIT ?""",
                (rs, row) -> new PendingProduct(rs.getLong(1), rs.getString(2), rs.getBigDecimal(3), rs.getString(4)),
                BATCH_SIZE);
        if (pending.isEmpty()) {
            return 0;
        }

        Answer answer;
        try {
            answer = ai.generateJson(prompt(pending), SCHEMA, Answer.class);
        } catch (AiUnavailableException e) {
            log.warn("Product categorization skipped: {}", e.getMessage());
            return 0;
        }

        Set<Long> requested = pending.stream().map(PendingProduct::id).collect(Collectors.toSet());
        int updated = 0;
        for (Answer.Item item : answer.items() == null ? List.<Answer.Item>of() : answer.items()) {
            // Ignore ids the model made up; unanswered products stay pending for the next run
            if (item.category() != null && requested.contains(item.id())) {
                updated += jdbc.update("UPDATE products SET category = ? WHERE id = ? AND category IS NULL",
                        item.category().name(), item.id());
            }
        }
        return updated;
    }

    private static String prompt(List<PendingProduct> products) {
        String lines = products.stream()
                .map(p -> p.id() + ": " + p.name() + p.size())
                .collect(Collectors.joining("\n"));
        return """
                Você classifica produtos de supermercados brasileiros. As descrições vêm de cupons fiscais,
                em maiúsculas e com abreviações (ex.: QJO = queijo, REFRIG = refrigerante, SAB = sabão ou sabonete).
                Escolha exatamente uma categoria para cada produto, usando o id informado.
                Sacolas, descartáveis e utensílios são UTILIDADES. Use OUTROS só quando nenhuma servir.

                Produtos:
                """ + lines;
    }

    private static final Map<String, Object> SCHEMA = Map.of(
            "type", "object",
            "properties", Map.of("items", Map.of(
                    "type", "array",
                    "items", Map.of(
                            "type", "object",
                            "properties", Map.of(
                                    "id", Map.of("type", "integer"),
                                    "category", Map.of("type", "string", "enum",
                                            Arrays.stream(ProductCategory.values()).map(Enum::name).toList())),
                            "required", List.of("id", "category")))),
            "required", List.of("items"));

    private record PendingProduct(long id, String name, BigDecimal measureValue, String measureUnit) {

        String size() {
            return measureValue == null ? "" : " (" + measureValue.stripTrailingZeros().toPlainString() + measureUnit + ")";
        }
    }

    record Answer(List<Item> items) {

        record Item(long id, ProductCategory category) {
        }
    }
}
