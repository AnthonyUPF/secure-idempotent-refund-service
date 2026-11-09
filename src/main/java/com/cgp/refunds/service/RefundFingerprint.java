package com.cgp.refunds.service;

import com.cgp.refunds.dto.RefundRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

public final class RefundFingerprint {

    private RefundFingerprint() {
    }

    public static String calculate(RefundRequest request) {
        String canonicalRequest = request.paymentId()
                + "|"
                + request.amount().stripTrailingZeros().toPlainString()
                + "|"
                + request.currency();

        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(
                    canonicalRequest.getBytes(StandardCharsets.UTF_8)
            );
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(
                    "SHA-256 algorithm is not available.",
                    exception
            );
        }
    }
}
