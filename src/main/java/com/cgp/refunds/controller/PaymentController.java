package com.cgp.refunds.controller;

import com.cgp.refunds.dto.PaymentRequest;
import com.cgp.refunds.dto.PaymentResponse;
import com.cgp.refunds.dto.PaymentUpdateRequest;
import com.cgp.refunds.service.PaymentService;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping
    public ResponseEntity<PaymentResponse> createPayment(
            @Valid @RequestBody PaymentRequest request) {

        PaymentResponse response = paymentService.createPayment(request);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping
    public ResponseEntity<List<PaymentResponse>> getAllPayments() {
        return ResponseEntity.ok(paymentService.getAllPayments());
    }

    @GetMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> getPayment(
            @PathVariable UUID paymentId) {

        return ResponseEntity.ok(paymentService.getPayment(paymentId));
    }

    @PatchMapping("/{paymentId}")
    public ResponseEntity<PaymentResponse> updatePayment(
            @PathVariable UUID paymentId,
            @Valid @RequestBody PaymentUpdateRequest request) {

        return ResponseEntity.ok(
                paymentService.updatePayment(paymentId, request)
        );
    }

    @DeleteMapping("/{paymentId}")
    public ResponseEntity<Void> deletePayment(
            @PathVariable UUID paymentId) {

        paymentService.deletePayment(paymentId);
        return ResponseEntity.noContent().build();
    }
}
