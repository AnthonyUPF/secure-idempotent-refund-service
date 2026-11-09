package com.cgp.refunds.repository;

import com.cgp.refunds.model.Refund;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.util.UUID;

public interface RefundRepository extends JpaRepository<Refund, UUID> {

    @Query("""
        SELECT COALESCE(SUM(r.amount), 0)
        FROM Refund r
        WHERE r.paymentId = :paymentId
        """)
    BigDecimal sumRefundedByPaymentId(
            @Param("paymentId") UUID paymentId
    );

    boolean existsByPaymentId(UUID paymentId);
}
