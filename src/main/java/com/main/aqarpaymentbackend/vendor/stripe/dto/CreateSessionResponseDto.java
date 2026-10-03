package com.main.aqarpaymentbackend.vendor.stripe.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.main.aqarpaymentbackend.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;


@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateSessionResponseDto {

    private String stripePaymentId;
    private String checkoutUrl;
    private Timestamp checkoutUrlExpiresAt;
    private String stripePaymentStatus;
    private String stripeStatus;
    private String cancelUrl;
    private String successUrl;
    private PaymentStatus paymentStatus;
}
