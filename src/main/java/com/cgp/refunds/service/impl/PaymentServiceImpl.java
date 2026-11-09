package com.cgp.refunds.service.impl;

import com.cgp.refunds.dto.PaymentRequest;
import com.cgp.refunds.dto.PaymentResponse;
import com.cgp.refunds.dto.PaymentUpdateRequest;
import com.cgp.refunds.model.Payment;
import com.cgp.refunds.model.PaymentStatus;
import com.cgp.refunds.repository.PaymentRepository;
import com.cgp.refunds.service.PaymentService;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.Currency;
import java.util.List;
import java.util.UUID;

@Service
@Transactional
public class PaymentServiceImpl implements PaymentService {

    private final PaymentRepository paymentRepository;

    public PaymentServiceImpl(PaymentRepository paymentRepository) {
        this.paymentRepository = paymentRepository;
    }

    @Override
    public PaymentResponse createPayment(PaymentRequest request) {
        validateCurrency(request.currency());

        Payment payment = new Payment(
                request.amount(),
                request.currency()
        );

        return PaymentResponse.from(paymentRepository.save(payment));
    }

    @Override
    @Transactional(readOnly = true)
    public List<PaymentResponse> getAllPayments() {
        return paymentRepository.findAll()
                .stream()
                .map(PaymentResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public PaymentResponse getPayment(UUID paymentId) {
        Payment payment = findPayment(paymentId);
        return PaymentResponse.from(payment);
    }

    @Override
    public PaymentResponse updatePayment(
            UUID paymentId,
            PaymentUpdateRequest request) {

        Payment payment = findPayment(paymentId);

        if (request.status() != PaymentStatus.CANCELLED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only payment cancellation is allowed."
            );
        }

        if (payment.getStatus() != PaymentStatus.CAPTURED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Only captured payments without refunds can be cancelled."
            );
        }

        payment.cancel();

        return PaymentResponse.from(payment);
    }

    @Override
    public void deletePayment(UUID paymentId) {
        Payment payment = findPayment(paymentId);

        if (payment.getStatus() == PaymentStatus.PARTIALLY_REFUNDED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payments with completed refunds cannot be deleted."
            );
        }

        paymentRepository.delete(payment);
    }

    private Payment findPayment(UUID paymentId) {
        return paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found."
                ));
    }

    private void validateCurrency(String currencyCode) {
        try {
            Currency.getInstance(currencyCode);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Invalid ISO 4217 currency code."
            );
        }
    }
}
