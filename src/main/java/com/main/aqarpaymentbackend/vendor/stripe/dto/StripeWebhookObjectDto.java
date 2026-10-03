package com.main.aqarpaymentbackend.vendor.stripe.dto;

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
public class StripeWebhookObjectDto {

    @JsonProperty("id")
    private String id;  // the "providerReference" in payment and payment dues tables
    @JsonProperty("object")
    private String object;
    @JsonProperty("client_reference_id")
    private String clientReferenceId;   // Payment id
    @JsonProperty("amount_total")
    private Long amountTotal;
    @JsonProperty("currency")
    private String currency;
    @JsonProperty("mode")
    private String mode;
    @JsonProperty("payment_status")
    private String paymentStatus;  // "paid" or "unpaid"
    @JsonProperty("status")
    private String status;         // "open", "complete", or "expired"
    @JsonProperty("payment_intent")
    private String paymentIntent;
}