package com.cgp.refunds.service;

import com.cgp.refunds.dto.RefundRequest;
import com.cgp.refunds.dto.RefundResponse;

import java.util.UUID;

public interface RefundService {

    RefundResponse createRefund(String idempotencyKey, RefundRequest request);

    RefundResponse getRefund(UUID refundId);
}
