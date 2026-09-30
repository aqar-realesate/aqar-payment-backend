package com.main.aqarpaymentbackend.vendor.fawaterk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.main.aqarpaymentbackend.model.enums.PaymentGateway;
import com.main.aqarpaymentbackend.model.enums.PaymentStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class SuccessTransactionResponseDto {

    private String status;
    private Integer paymentDueId;
    private Integer customerId;
    private String customerEmail;
    private Integer unitId;
    private Integer paymentId;
    private PaymentStatus paymentStatus;
    private String fawaterkIntentKey;
    private Integer fawaterkTransactionId;
    private String fawaterkTransactionLink;
    private String fawaterkTransactionCreatedAt;
    private Integer paidFlag;
    private String paidAt;
    private PaymentGateway paymentGateway;
    private String statusText;
    private BigDecimal amoundPaid;
    private String currency;
}
