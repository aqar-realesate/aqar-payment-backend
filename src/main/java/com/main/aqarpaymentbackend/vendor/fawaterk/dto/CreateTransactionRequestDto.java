package com.main.aqarpaymentbackend.vendor.fawaterk.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class CreateTransactionRequestDto {

    @JsonProperty("payment_method_id")
    private Integer paymentMethodId;
    @JsonProperty("cartTotal")
    private BigDecimal cartTotal;
    @JsonProperty("currency")
    private String currency;
    @JsonProperty("customer")
    private CustomerInfoDto customer;
    @JsonProperty("cartItems")
    private List<CartItemDto> cartItems;
    @JsonProperty("redirectionUrls")
    private RedirectionUrlsDto redirectionUrls;
    @JsonProperty("sendEmail")
    private Boolean sendEmail;
}
