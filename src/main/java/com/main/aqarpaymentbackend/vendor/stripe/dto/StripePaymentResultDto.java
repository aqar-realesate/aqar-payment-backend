package com.main.aqarpaymentbackend.vendor.stripe.dto;

import com.main.aqarpaymentbackend.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.sql.Timestamp;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class StripePaymentResultDto {

    private Integer paymentId;
    private Integer paymentDueId;
    private String checkoutSessionId;
    private PaymentStatus paymentStatus;
    private PaymentStatus paymentDueStatus;
    private BigDecimal amount;
    private String currency;
    private Timestamp paidAt;

}
