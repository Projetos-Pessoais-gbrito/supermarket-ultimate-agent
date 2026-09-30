package com.supermarketagent.catalog;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreProductRepository extends JpaRepository<StoreProduct, Long> {

    /** Inserts the store product or refreshes its description/unit, safely under concurrent imports. */
    @Query(value = """
            INSERT INTO store_products (store_id, store_code, description, unit)
            VALUES (:storeId, :storeCode, :description, :unit)
            ON CONFLICT (store_id, store_code) DO UPDATE
                SET description = EXCLUDED.description, unit = EXCLUDED.unit
            RETURNING id""", nativeQuery = true)
    long upsert(@Param("storeId") long storeId, @Param("storeCode") String storeCode,
                @Param("description") String description, @Param("unit") String unit);
}
