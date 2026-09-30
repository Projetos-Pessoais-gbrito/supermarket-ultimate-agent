package com.supermarketagent.receipt.persistence;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    @Query("select r.id from Receipt r where r.userId = :userId and r.accessKey = :accessKey")
    Optional<Long> findIdByUserIdAndAccessKey(@Param("userId") long userId, @Param("accessKey") String accessKey);
}
