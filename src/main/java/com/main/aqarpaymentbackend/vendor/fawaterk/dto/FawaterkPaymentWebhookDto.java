package com.main.aqarpaymentbackend.vendor.fawaterk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class FawaterkPaymentWebhookDto {

    @JsonProperty("transaction_key")
    private String transactionKey;

    @JsonProperty("transaction_id")
    private Long transactionId;

    @JsonProperty("payment_method")
    private String paymentMethod;

    private String status;

    private BigDecimal paidAmount;

    private String paidCurrency;

    private String transactionHashKey;
}
