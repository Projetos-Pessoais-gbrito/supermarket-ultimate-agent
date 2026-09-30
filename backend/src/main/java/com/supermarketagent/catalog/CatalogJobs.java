package com.supermarketagent.catalog;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Background catalog maintenance; a separate bean so each batch runs in its own transaction. */
@Component
class CatalogJobs {

    private static final Logger log = LoggerFactory.getLogger(CatalogJobs.class);

    private final ProductMatcher matcher;
    private final ProductCategorizer categorizer;

    CatalogJobs(ProductMatcher matcher, ProductCategorizer categorizer) {
        this.matcher = matcher;
        this.categorizer = categorizer;
    }

    /** Runs outside imports so scanning a receipt never waits for the AI. */
    @Scheduled(fixedDelayString = "${app.catalog.categorize-interval:PT2M}", initialDelayString = "PT2M")
    void categorizePendingProducts() {
        int categorized;
        do {
            categorized = categorizer.categorizePending();
            if (categorized > 0) {
                log.info("Categorized {} products", categorized);
            }
        } while (categorized == ProductCategorizer.BATCH_SIZE);
    }

    /** Safety net for anything import-time matching missed (e.g. a failed run or older data). */
    @Scheduled(fixedDelayString = "${app.catalog.match-interval:PT5M}", initialDelayString = "PT1M")
    void linkPendingStoreProducts() {
        int linked;
        do {
            linked = matcher.linkUnlinkedStoreProducts();
            if (linked > 0) {
                log.info("Linked {} store products to canonical products", linked);
            }
        } while (linked == ProductMatcher.BATCH_SIZE);
    }
}
