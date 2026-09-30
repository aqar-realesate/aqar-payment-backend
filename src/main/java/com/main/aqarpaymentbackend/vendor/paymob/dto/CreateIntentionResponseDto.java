package com.main.aqarpaymentbackend.vendor.paymob.dto;

import com.main.aqarpaymentbackend.model.enums.PaymentGateway;
import com.main.aqarpaymentbackend.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateIntentionResponseDto {

    private Integer paymentId;
    private String checkoutUrl;
    private Instant checkoutExpiresAt;
    private PaymentStatus paymentStatus;
    private PaymentGateway paymentGateway;
}