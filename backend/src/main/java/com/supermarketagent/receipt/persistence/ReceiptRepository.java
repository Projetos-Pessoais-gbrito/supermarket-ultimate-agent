package com.supermarketagent.receipt.persistence;

import com.supermarketagent.receipt.query.ReceiptSummary;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface ReceiptRepository extends JpaRepository<Receipt, Long> {

    @Query("select r.id from Receipt r where r.userId = :userId and r.accessKey = :accessKey")
    Optional<Long> findIdByUserIdAndAccessKey(@Param("userId") long userId, @Param("accessKey") String accessKey);

    @Query(value = """
            select new com.supermarketagent.receipt.query.ReceiptSummary(
                r.id, coalesce(s.displayName, s.name), r.issuedAt, r.totalAmount, size(r.items))
            from Receipt r join r.store s
            where r.userId = :userId
            order by r.issuedAt desc, r.id desc""",
            countQuery = "select count(r) from Receipt r where r.userId = :userId")
    Page<ReceiptSummary> findSummariesByUserId(@Param("userId") long userId, Pageable pageable);

    @EntityGraph(attributePaths = {"store", "items", "items.storeProduct"})
    Optional<Receipt> findByIdAndUserId(long id, long userId);
}
