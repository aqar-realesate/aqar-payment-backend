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
public class StripeWebhookRequestDto {

    @JsonProperty("id")
    private String id;
    @JsonProperty("type")
    private String type;    // the webhook event to handle payment status in database
    @JsonProperty("pending_webhooks")
    private Integer pendingWebhooks;
    @JsonProperty("livemode")
    private boolean liveMode;   // ensure that we are in test mode or production mode
    @JsonProperty("data")
    private StripeWebhookDataDto data;
}
