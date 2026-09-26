package com.main.aqarpaymentbackend.vendor.fawaterk.dto;

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

    private BigDecimal cartTotal;
    private String currency;
    private CustomerDetailsDto customer;
    private List<CartItemDto> cartItems;
}
