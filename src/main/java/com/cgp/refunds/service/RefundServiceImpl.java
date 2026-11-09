package com.cgp.refunds.service;

import com.cgp.refunds.dto.RefundRequest;
import com.cgp.refunds.dto.RefundResponse;
import com.cgp.refunds.model.IdempotencyRecord;
import com.cgp.refunds.model.Refund;
import com.cgp.refunds.repository.IdempotencyRecordRepository;
import com.cgp.refunds.repository.RefundRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class RefundServiceImpl implements RefundService {

    private static final Pattern IDEMPOTENCY_KEY_PATTERN =
            Pattern.compile("^[A-Za-z0-9._:-]{1,128}$");

    private final RefundTransactionService transactionService;
    private final IdempotencyRecordRepository idempotencyRepository;
    private final RefundRepository refundRepository;

    public RefundServiceImpl(
            RefundTransactionService transactionService,
            IdempotencyRecordRepository idempotencyRepository,
            RefundRepository refundRepository
    ) {
        this.transactionService = transactionService;
        this.idempotencyRepository = idempotencyRepository;
        this.refundRepository = refundRepository;
    }

    @Override
    public RefundResponse createRefund(
            String idempotencyKey,
            RefundRequest request
    ) {
        if (idempotencyKey == null
                || !IDEMPOTENCY_KEY_PATTERN.matcher(idempotencyKey).matches()) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A valid Idempotency-Key header is required."
            );
        }

        String fingerprint = RefundFingerprint.calculate(request);

        try {
            return transactionService.createRefund(
                    idempotencyKey,
                    fingerprint,
                    request
            );
        } catch (DataIntegrityViolationException exception) {
            return resolveDuplicate(idempotencyKey, fingerprint);
        }
    }

    @Override
    public RefundResponse getRefund(UUID refundId) {
        Refund refund = refundRepository.findById(refundId)
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.NOT_FOUND,
                        "Refund not found."
                ));

        return RefundResponse.from(refund);
    }

    private RefundResponse resolveDuplicate(
            String idempotencyKey,
            String fingerprint
    ) {
        IdempotencyRecord existingRecord =
                idempotencyRepository.findByKey(idempotencyKey)
                        .orElseThrow(() -> new ResponseStatusException(
                                HttpStatus.CONFLICT,
                                "The request could not be processed."
                        ));

        if (!existingRecord.getFingerprint().equals(fingerprint)) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "Idempotency key was already used with a different request."
            );
        }

        if (existingRecord.getRefundId() == null) {
            throw new ResponseStatusException(
                    HttpStatus.CONFLICT,
                    "The original request has not completed."
            );
        }

        Refund refund = refundRepository
                .findById(existingRecord.getRefundId())
                .orElseThrow(() -> new ResponseStatusException(
                        HttpStatus.INTERNAL_SERVER_ERROR,
                        "The original refund result could not be retrieved."
                ));

        return RefundResponse.from(refund);
    }
}
