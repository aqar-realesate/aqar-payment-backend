package com.main.aqarpaymentbackend.vendor.fawaterk.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaidWebhookRequestDto {

    @JsonProperty("transaction_key")
    private String intentKey;
    @JsonProperty("transaction_id")
    private Integer transactionId;
    @JsonProperty("payment_method")
    private String paymentMethod;
    @JsonProperty("status")
    private String status;
    @JsonProperty("paidAmount")
    private String paidAmount;
    @JsonProperty("paidCurrency")
    private String paidCurrency;
    @JsonProperty("customerData")
    private PaidWebhookCustomerDataRequestDto customerData;
    @JsonProperty("transactionHashKey")
    private String transactionHashKey;
}
