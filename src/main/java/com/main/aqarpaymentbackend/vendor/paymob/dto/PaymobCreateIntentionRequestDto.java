package com.main.aqarpaymentbackend.vendor.paymob.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymobCreateIntentionRequestDto {

    @JsonProperty("amount")
    private BigDecimal amount;

    @JsonProperty("currency")
    private String currency;

    // Integration IDs offered at checkout
    @JsonProperty("payment_methods")
    private List<Integer> paymentMethods;

    @JsonProperty("items")
    private List<ItemsRequestDto> items;

    @JsonProperty("billing_data")
    private PaymobBillingDataDto billingData;

    // Our unique reference for this payment attempt
    @JsonProperty("special_reference")
    private String specialReference;

    @JsonProperty("expiration")
    private Integer expiration;

    // Webhook url
    @JsonProperty("notification_url")
    private String notificationUrl;

    // Redirect url
    @JsonProperty("redirection_url")
    private String redirectionUrl;
}
