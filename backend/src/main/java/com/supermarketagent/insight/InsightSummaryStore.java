package com.supermarketagent.insight;

import java.util.List;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

/** Persists AI tips per user and set of facts, so they survive restarts and stay stable until data changes. */
@Component
class InsightSummaryStore {

    private static final TypeReference<List<String>> TIPS = new TypeReference<>() {
    };

    private final JdbcTemplate jdbc;
    private final JsonMapper json;

    InsightSummaryStore(JdbcTemplate jdbc, JsonMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    Optional<List<String>> find(long userId, String factsHash) {
        return jdbc.query("SELECT tips::text FROM insight_summaries WHERE user_id = ? AND facts_hash = ?",
                        (rs, row) -> json.readValue(rs.getString(1), TIPS), userId, factsHash)
                .stream().findFirst();
    }

    /** Keeps only the latest summary per user: older facts will not come back. */
    @Transactional
    void save(long userId, String factsHash, List<String> tips) {
        jdbc.update("""
                INSERT INTO insight_summaries (user_id, facts_hash, tips) VALUES (?, ?, CAST(? AS jsonb))
                ON CONFLICT (user_id, facts_hash) DO UPDATE SET tips = EXCLUDED.tips, created_at = now()""",
                userId, factsHash, json.writeValueAsString(tips));
        jdbc.update("DELETE FROM insight_summaries WHERE user_id = ? AND facts_hash <> ?", userId, factsHash);
    }
}
