package com.cgp.refunds.dto;

import com.cgp.refunds.model.Refund;
import com.cgp.refunds.model.RefundStatus;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

public record RefundResponse(
        UUID refundId,
        UUID paymentId,
        BigDecimal amount,
        String currency,
        RefundStatus status,
        Instant createdAt
) {
    public static RefundResponse from(Refund refund) {
        return new RefundResponse(
                refund.getId(),
                refund.getPaymentId(),
                refund.getAmount(),
                refund.getCurrency(),
                refund.getStatus(),
                refund.getCreatedAt()
        );
    }
}
