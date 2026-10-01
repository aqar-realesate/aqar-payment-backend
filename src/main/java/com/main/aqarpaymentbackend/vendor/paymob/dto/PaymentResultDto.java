package com.main.aqarpaymentbackend.vendor.paymob.dto;

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
public class PaymentResultDto {

    private Integer paymentId;
    private Integer paymentDueId;
    private PaymentStatus paymentStatus;
    private PaymentStatus paymentDueStatus;
    private BigDecimal amount;
    private String currency;
    private Timestamp paidAt;
}
