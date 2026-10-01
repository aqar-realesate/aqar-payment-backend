package com.main.aqarpaymentbackend.vendor.paymob.dto;

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
public class WebhookObjBodyDto {

    @JsonProperty("id")
    private Integer transactionId;  // save into "providerTransactionId" in payment table when webhook fires
    @JsonProperty("pending")
    private Boolean pending;
    @JsonProperty("success")
    private Boolean success;
    @JsonProperty("amount_cents")
    private BigDecimal paymentAmount;
    @JsonProperty("currency")
    private String paymentCurrency;
    @JsonProperty("integration_id")
    private Integer integrationId;  // matches the "cardIntegrationId" in PaymobService or "paymentMethodId" in Payment table
    @JsonProperty("error_occured")
    private Boolean paymentErrorCheck;
    @JsonProperty("is_capture")
    private Boolean isCapture;
    @JsonProperty("is_voided")
    private Boolean isVoided;
    @JsonProperty("created_at")
    private String createdAt;
    @JsonProperty("has_parent_transaction")
    private Boolean hasParentTransaction;
    @JsonProperty("is_3d_secure")
    private Boolean is3dSecure;
    @JsonProperty("is_refunded")
    private Boolean isRefunded;
    @JsonProperty("is_auth")
    private Boolean isAuth;
    @JsonProperty("is_standalone_payment")
    private Boolean isStandalonePayment;
    @JsonProperty("owner")
    private Integer owner;
    @JsonProperty("order")
    private WebhookObjOrderBodyDto order;
    @JsonProperty("source_data")
    private WebhookObjSourceDataBodyDto sourceData;
}
