package com.main.aqarpaymentbackend.vendor.paymob.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
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
@JsonIgnoreProperties(ignoreUnknown = true)
public class PaymobCreateIntentionResponseDto {

    @JsonProperty("payment_keys")
    private List<Object> paymentKeys;
    @JsonProperty("id")
    private String id;
    @JsonProperty("intention_order_id")
    private Long intentionOrderId;
    @JsonProperty("intention_detail")
    private Object intentionDetail;
    // Used to open checkout for this particular intention
    @JsonProperty("client_secret")
    private String clientSecret;
    @JsonProperty("payment_methods")
    private List<Object> paymentMethods;
    // Our payment attempt reference returned by Paymob
    @JsonProperty("special_reference")
    private String specialReference;
    @JsonProperty("status")
    private String status;
    @JsonProperty("confirmed")
    private Boolean confirmed;
    @JsonProperty("created")
    private String created;
}
