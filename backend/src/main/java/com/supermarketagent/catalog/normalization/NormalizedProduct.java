package com.supermarketagent.catalog.normalization;

import java.util.Optional;

/**
 * @param name    upper-case, accent-free description without the package size, used for matching
 * @param measure package size when the description states one
 */
public record NormalizedProduct(String name, Optional<Measure> measure) {
}
