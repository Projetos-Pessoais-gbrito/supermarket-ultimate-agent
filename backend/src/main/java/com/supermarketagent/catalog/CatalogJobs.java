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

    CatalogJobs(ProductMatcher matcher) {
        this.matcher = matcher;
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
