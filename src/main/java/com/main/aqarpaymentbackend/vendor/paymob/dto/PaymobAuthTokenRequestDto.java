package com.main.aqarpaymentbackend.vendor.paymob.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PaymobAuthTokenRequestDto {

    @JsonProperty("api_key")
    private String apikey;
}
