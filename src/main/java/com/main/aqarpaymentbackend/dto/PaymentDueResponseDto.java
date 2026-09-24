package com.main.aqarpaymentbackend.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class PaymentDueResponseDto {

    private Integer dueId;
    private Integer customerId;
    private Integer unitId;
    private Integer requestId;
    private BigDecimal amount;
    private String currency;
    private LocalDateTime dueDate;
    private String description;
}
