package com.supermarketagent.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByCnpj(String cnpj);

    /** Inserts the store or refreshes its name/address, safely under concurrent imports. */
    @Query(value = """
            INSERT INTO stores (cnpj, name, address, state_code)
            VALUES (:cnpj, :name, :address, :stateCode)
            ON CONFLICT (cnpj) DO UPDATE
                SET name = EXCLUDED.name, address = EXCLUDED.address, updated_at = now()
            RETURNING id""", nativeQuery = true)
    long upsert(@Param("cnpj") String cnpj, @Param("name") String name,
                @Param("address") String address, @Param("stateCode") String stateCode);
}
