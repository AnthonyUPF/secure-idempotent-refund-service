package com.cgp.refunds.payment;

import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockPaymentProcessor implements PaymentProcessor {

    @Override
    public void processRefund(
            UUID paymentId,
            BigDecimal amount,
            String currency
    ) {
        // Simula una operación correcta con un proveedor de pagos.
        // No se realizan llamadas externas ni se manejan datos de tarjeta.
    }
}
