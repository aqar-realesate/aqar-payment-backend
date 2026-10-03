package com.main.aqarpaymentbackend.vendor.stripe.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.sql.Timestamp;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
public class StripeSessionResponseDto {

    @JsonProperty("id")
    private String id;   // save into Payment in "providerReference"
    @JsonProperty("url")
    private String url;
    @JsonProperty("expires_at")
    private Timestamp expiresAt;
    @JsonProperty("object")
    private String object;
    @JsonProperty("client_reference_id")
    private String clientReferenceId;   // this our paymentId
    @JsonProperty("amount_total")
    private Long amountTotal;
    @JsonProperty("currency")
    private String currency;
    @JsonProperty("payment_status")
    private String paymentStatus;
    @JsonProperty("status")
    private String status;
    @JsonProperty("cancel_url")
    private String cancelUrl;
    @JsonProperty("success_url")
    private String successUrl;
}
