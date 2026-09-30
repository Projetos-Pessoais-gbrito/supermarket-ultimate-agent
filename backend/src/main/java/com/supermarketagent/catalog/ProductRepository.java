package com.supermarketagent.catalog;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ProductRepository extends JpaRepository<Product, Long> {

    /** Most similar product with exactly the same package size (both may be absent). */
    @Query(value = """
            SELECT id FROM products
            WHERE measure_value IS NOT DISTINCT FROM CAST(:measureValue AS numeric)
              AND measure_unit IS NOT DISTINCT FROM CAST(:measureUnit AS varchar)
              AND similarity(normalized_name, :name) >= :threshold
            ORDER BY similarity(normalized_name, :name) DESC, id
            LIMIT 1""", nativeQuery = true)
    Optional<Long> findBestMatch(@Param("name") String name,
                                 @Param("measureValue") BigDecimal measureValue,
                                 @Param("measureUnit") String measureUnit,
                                 @Param("threshold") double threshold);

    @Query(value = """
            INSERT INTO products (normalized_name, measure_value, measure_unit)
            VALUES (:name, CAST(:measureValue AS numeric), CAST(:measureUnit AS varchar))
            RETURNING id""", nativeQuery = true)
    long insert(@Param("name") String name,
                @Param("measureValue") BigDecimal measureValue,
                @Param("measureUnit") String measureUnit);

    /** Store products not linked yet; locked so concurrent matchers never process the same row. */
    @Query(value = """
            SELECT id, description FROM store_products
            WHERE product_id IS NULL
            ORDER BY id
            LIMIT :limit
            FOR UPDATE SKIP LOCKED""", nativeQuery = true)
    List<Object[]> lockUnlinkedStoreProducts(@Param("limit") int limit);

    @Modifying
    @Query(value = "UPDATE store_products SET product_id = :productId WHERE id = :storeProductId", nativeQuery = true)
    void link(@Param("storeProductId") long storeProductId, @Param("productId") long productId);
}
