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

    /**
     * Creates the store product if it is new and never changes an existing description: used for
     * pages the backend could not verify with SEFAZ.
     */
    @Query(value = """
            INSERT INTO store_products (store_id, store_code, description, unit)
            VALUES (:storeId, :storeCode, :description, :unit)
            ON CONFLICT (store_id, store_code) DO UPDATE SET store_code = store_products.store_code
            RETURNING id""", nativeQuery = true)
    long insertIfAbsent(@Param("storeId") long storeId, @Param("storeCode") String storeCode,
                        @Param("description") String description, @Param("unit") String unit);
}
