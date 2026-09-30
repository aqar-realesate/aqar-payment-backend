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
public class GetPaymentMethodsDataDto {

    @JsonProperty("payment_method_id")
    private Integer paymentMethodId;
    @JsonProperty("name_en")
    private String nameEn;
    @JsonProperty("name_ar")
    private String nameAr;
    @JsonProperty("redirect")
    private String redirect;
    @JsonProperty("commission_on_customer")
    private Integer commissionOnCustomer;
    @JsonProperty("integration_status")
    private Integer integrationStatus;
}
