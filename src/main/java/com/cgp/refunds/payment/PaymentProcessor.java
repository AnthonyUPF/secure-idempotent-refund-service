package com.cgp.refunds.payment;

import java.math.BigDecimal;
import java.util.UUID;

public interface PaymentProcessor {

    void processRefund(
            UUID paymentId,
            BigDecimal amount,
            String currency
    );
}
