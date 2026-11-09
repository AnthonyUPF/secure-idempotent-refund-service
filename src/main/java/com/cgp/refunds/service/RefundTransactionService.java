package com.cgp.refunds.service;

import com.cgp.refunds.dto.RefundRequest;
import com.cgp.refunds.dto.RefundResponse;
import com.cgp.refunds.model.IdempotencyRecord;
import com.cgp.refunds.model.Payment;
import com.cgp.refunds.model.PaymentStatus;
import com.cgp.refunds.model.Refund;
import com.cgp.refunds.payment.PaymentProcessor;
import com.cgp.refunds.repository.IdempotencyRecordRepository;
import com.cgp.refunds.repository.PaymentRepository;
import com.cgp.refunds.repository.RefundRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.UUID;

@Service
public class RefundTransactionService {

    private final IdempotencyRecordRepository idempotencyRepository;
    private final PaymentRepository paymentRepository;
    private final RefundRepository refundRepository;
    private final PaymentProcessor paymentProcessor;

    public RefundTransactionService(
            IdempotencyRecordRepository idempotencyRepository,
            PaymentRepository paymentRepository,
            RefundRepository refundRepository,
            PaymentProcessor paymentProcessor
    ) {
        this.idempotencyRepository = idempotencyRepository;
        this.paymentRepository = paymentRepository;
        this.refundRepository = refundRepository;
        this.paymentProcessor = paymentProcessor;
    }

    @Transactional
    public RefundResponse createRefund(
            String idempotencyKey,
            String fingerprint,
            RefundRequest request
    ) {
        IdempotencyRecord record =
                idempotencyRepository.saveAndFlush(
                        new IdempotencyRecord(idempotencyKey, fingerprint)
                );

        Payment payment = paymentRepository
                .findByIdForUpdate(request.paymentId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Payment not found."
                ));

        if (payment.getStatus() == PaymentStatus.CANCELLED
                || payment.getStatus() == PaymentStatus.REFUNDED) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Payment cannot be refunded in its current state."
            );
        }

        if (!payment.getCurrency().equals(request.currency())) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Refund currency must match payment currency."
            );
        }

        BigDecimal alreadyRefunded =
                refundRepository.sumRefundedByPaymentId(payment.getId());

        BigDecimal remainingAmount =
                payment.getAmount().subtract(alreadyRefunded);

        if (request.amount().compareTo(remainingAmount) > 0) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Refund amount exceeds the remaining refundable balance."
            );
        }

        paymentProcessor.processRefund(
                payment.getId(),
                request.amount(),
                request.currency()
        );

        Refund refund = refundRepository.save(
                new Refund(
                        payment.getId(),
                        request.amount(),
                        request.currency()
                )
        );

        BigDecimal newTotalRefunded =
                alreadyRefunded.add(request.amount());

        payment.updateRefundStatus(newTotalRefunded);

        record.complete(refund.getId());

        return RefundResponse.from(refund);
    }
}
