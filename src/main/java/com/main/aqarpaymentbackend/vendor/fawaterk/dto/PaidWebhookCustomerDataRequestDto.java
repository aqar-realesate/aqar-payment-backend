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
public class PaidWebhookCustomerDataRequestDto {

    @JsonProperty("customer_unique_id")
    private String customerUniqueId;
    @JsonProperty("customer_first_name")
    private String customerFirstName;
    @JsonProperty("customer_last_name")
    private String customerLastName;
    @JsonProperty("customer_email")
    private String customerEmail;
}
