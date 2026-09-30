package com.main.aqarpaymentbackend.vendor.paymob.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymobCreateIntentionRequestDto {

    @JsonProperty("amount")
    private Long amount;

    @JsonProperty("currency")
    private String currency;

    // Integration IDs offered at checkout
    @JsonProperty("payment_methods")
    private List<Integer> paymentMethods;

    @JsonProperty("billing_data")
    private PaymobBillingDataDto billingData;

    // Our unique reference for this payment attempt
    @JsonProperty("special_reference")
    private String specialReference;

    // Where Paymob sends payment results to our backend
    @JsonProperty("notification_url")
    private String notificationUrl;

    // Where the customer’s browser goes after payment
    @JsonProperty("redirection_url")
    private String redirectionUrl;
}
