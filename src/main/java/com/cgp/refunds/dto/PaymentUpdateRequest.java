package com.cgp.refunds.dto;

import com.cgp.refunds.model.PaymentStatus;
import jakarta.validation.constraints.NotNull;

public record PaymentUpdateRequest(

        @NotNull
        PaymentStatus status

) {
}
