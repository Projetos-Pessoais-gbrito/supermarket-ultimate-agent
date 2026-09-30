package com.supermarketagent.catalog;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface StoreRepository extends JpaRepository<Store, Long> {

    Optional<Store> findByCnpj(String cnpj);

    /** Inserts the store or refreshes its name/address, safely under concurrent imports. */
    @Query(value = """
            INSERT INTO stores (cnpj, name, display_name, address, state_code)
            VALUES (:cnpj, :name, :displayName, :address, :stateCode)
            ON CONFLICT (cnpj) DO UPDATE
                SET name = EXCLUDED.name, display_name = EXCLUDED.display_name, address = EXCLUDED.address,
                    updated_at = now()
            RETURNING id""", nativeQuery = true)
    long upsert(@Param("cnpj") String cnpj, @Param("name") String name, @Param("displayName") String displayName,
                @Param("address") String address, @Param("stateCode") String stateCode);

    /**
     * Creates the store if it is new and never changes an existing one: used for pages the backend
     * could not verify with SEFAZ, so they cannot rename a store other users see.
     */
    @Query(value = """
            INSERT INTO stores (cnpj, name, display_name, address, state_code)
            VALUES (:cnpj, :name, :displayName, :address, :stateCode)
            ON CONFLICT (cnpj) DO UPDATE SET cnpj = stores.cnpj
            RETURNING id""", nativeQuery = true)
    long insertIfAbsent(@Param("cnpj") String cnpj, @Param("name") String name,
                        @Param("displayName") String displayName, @Param("address") String address,
                        @Param("stateCode") String stateCode);
}
