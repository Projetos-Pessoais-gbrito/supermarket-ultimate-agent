package com.supermarketagent.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Gives stores imported before display names existed their friendly name, once, at startup. */
@Component
class StoreDisplayNameBackfill implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(StoreDisplayNameBackfill.class);

    private final JdbcTemplate jdbc;

    StoreDisplayNameBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void run(ApplicationArguments args) {
        int updated = backfill();
        if (updated > 0) {
            log.info("Set display names for {} stores", updated);
        }
    }

    int backfill() {
        var stores = jdbc.query("SELECT id, cnpj, name FROM stores WHERE display_name IS NULL",
                (rs, row) -> new Object[] {rs.getLong("id"), rs.getString("cnpj"), rs.getString("name")});
        for (Object[] store : stores) {
            jdbc.update("UPDATE stores SET display_name = ? WHERE id = ? AND display_name IS NULL",
                    StoreNames.displayName((String) store[1], (String) store[2]), store[0]);
        }
        return stores.size();
    }
}
