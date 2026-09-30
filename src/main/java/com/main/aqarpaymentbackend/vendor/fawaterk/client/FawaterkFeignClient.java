package com.main.aqarpaymentbackend.vendor.fawaterk.client;

import com.main.aqarpaymentbackend.vendor.fawaterk.dto.*;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;


@FeignClient(
        name = "fawaterk-integration",
        url = "${fawaterk.base-url}"
)
public interface FawaterkFeignClient {

    @PostMapping("/oauth/token")
    OAuthResponseDto fawaterkOAuth(@RequestBody OAuthRequestDto request);

    @GetMapping("/api/v3/getTrPaymentmethods")
    GetPaymentMethodsDto getPaymentMethods(@RequestHeader("Authorization") String token);

    @PostMapping("/api/v3/createTransaction")
    CreateTransactionResponseDto createTransaction(
            @RequestHeader("Authorization") String token,
            @RequestBody CreateTransactionRequestDto request);

    @PostMapping("/api/v3/getTransactionData")
    GetTransactionDataResponseDto getTransactionData(
            @RequestHeader("Authorization") String token,
            @RequestBody GetTransactionDataRequestDto request);
}
