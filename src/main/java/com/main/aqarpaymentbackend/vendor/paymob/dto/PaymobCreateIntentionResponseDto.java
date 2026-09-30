package com.main.aqarpaymentbackend.vendor.paymob.dto;

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
public class PaymobCreateIntentionResponseDto {

    @JsonProperty("id")
    private String id;
    @JsonProperty("intention_order_id")
    private Long intentionOrderId;
    // Used to open checkout for this particular intention
    @JsonProperty("client_secret")
    private String clientSecret;
    // Our payment attempt reference returned by Paymob
    @JsonProperty("special_reference")
    private String specialReference;
    @JsonProperty("status")
    private String status;
}
