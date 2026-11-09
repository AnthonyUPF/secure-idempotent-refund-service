package com.cgp.refunds.service;

import com.cgp.refunds.dto.PaymentRequest;
import com.cgp.refunds.dto.PaymentResponse;
import com.cgp.refunds.dto.PaymentUpdateRequest;

import java.util.List;
import java.util.UUID;

public interface PaymentService {

    PaymentResponse createPayment(PaymentRequest request);

    List<PaymentResponse> getAllPayments();

    PaymentResponse getPayment(UUID paymentId);

    PaymentResponse updatePayment(
            UUID paymentId,
            PaymentUpdateRequest request
    );

    void deletePayment(UUID paymentId);
}
