package com.cgp.refunds.controller;

import com.cgp.refunds.dto.RefundRequest;
import com.cgp.refunds.dto.RefundResponse;
import com.cgp.refunds.service.RefundService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.net.URI;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/refunds")
public class RefundController {

    private final RefundService refundService;

    public RefundController(RefundService refundService) {
        this.refundService = refundService;
    }

    @PostMapping
    public ResponseEntity<RefundResponse> createRefund(
            @RequestHeader("Idempotency-Key") String idempotencyKey,
            @Valid @RequestBody RefundRequest request
    ) {
        RefundResponse response =
                refundService.createRefund(idempotencyKey, request);

        return ResponseEntity
                .created(URI.create("/api/v1/refunds/" + response.refundId()))
                .body(response);
    }

    @GetMapping("/{refundId}")
    public ResponseEntity<RefundResponse> getRefund(
            @PathVariable UUID refundId
    ) {
        return ResponseEntity.ok(refundService.getRefund(refundId));
    }
}
