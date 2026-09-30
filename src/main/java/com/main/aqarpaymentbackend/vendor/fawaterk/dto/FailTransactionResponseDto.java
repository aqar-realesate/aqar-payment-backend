package com.main.aqarpaymentbackend.vendor.fawaterk.dto;

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
public class FailTransactionResponseDto {

    private String status;
    private Integer paymentDueId;
    private Integer customerId;
    private String customerEmail;
    private Integer unitId;
    private Integer paymentId;
    private PaymentStatus paymentStatus;
    private String fawaterkIntentKey;
    private String fawaterkTransactionLink;
    private String fawaterkTransactionCreatedAt;
    private Integer paidFlag;
    private PaymentGateway paymentGateway;
    private String statusText;
    private String errorMessage;
    private BigDecimal amount;
    private String currency;
}
