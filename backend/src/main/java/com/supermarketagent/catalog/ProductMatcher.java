package com.supermarketagent.catalog;

import com.supermarketagent.catalog.normalization.Measure;
import com.supermarketagent.catalog.normalization.NormalizedProduct;
import com.supermarketagent.catalog.normalization.ProductNameNormalizer;
import java.math.BigDecimal;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Links each store product to a canonical product so prices can be compared across stores.
 *
 * <p>Two descriptions are the same product when their package sizes are identical and their
 * normalized names are similar (pg_trgm). The default threshold of 0.6 was calibrated on real
 * names: spelling variants of one product score ~0.8, while the same item from another brand
 * ("LEITE UHT ITALAC" vs "LEITE UHT PIRACANJUBA") scores ~0.5 and must stay separate.
 */
@Service
public class ProductMatcher {

    static final int BATCH_SIZE = 200;

    private final ProductRepository products;
    private final double threshold;

    ProductMatcher(ProductRepository products, @Value("${app.catalog.match-threshold:0.6}") double threshold) {
        this.products = products;
        this.threshold = threshold;
    }

    /** Links up to one batch of unlinked store products; returns how many were linked. */
    @Transactional
    public int linkUnlinkedStoreProducts() {
        int linked = 0;
        for (Object[] row : products.lockUnlinkedStoreProducts(BATCH_SIZE)) {
            long storeProductId = ((Number) row[0]).longValue();
            NormalizedProduct normalized = ProductNameNormalizer.normalize((String) row[1]);
            BigDecimal measureValue = normalized.measure().map(Measure::amount).orElse(null);
            String measureUnit = normalized.measure().map(m -> m.unit().symbol()).orElse(null);

            long productId = products.findBestMatch(normalized.name(), measureValue, measureUnit, threshold)
                    .orElseGet(() -> products.insert(normalized.name(), measureValue, measureUnit));
            products.link(storeProductId, productId);
            linked++;
        }
        return linked;
    }
}
