package com.main.aqarpaymentbackend.vendor.fawaterk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class FailedWebhookRequestDto {

    @JsonProperty("transaction_key")
    private String intentKey;
    @JsonProperty("transaction_id")
    private Integer transactionId;
    @JsonProperty("payment_method")
    private String paymentMethod;
    @JsonProperty("paidAmount")
    private String paidAmount;
    @JsonProperty("paidCurrency")
    private String paidCurrency;
    @JsonProperty("hashKey")
    private String hashKey;
    @JsonProperty("errorMessage")
    private String errorMessage;
}
