package com.main.aqarpaymentbackend.vendor.paymob.client;

import com.main.aqarpaymentbackend.vendor.paymob.dto.PaymobAuthTokenRequestDto;
import com.main.aqarpaymentbackend.vendor.paymob.dto.PaymobAuthTokenResponseDto;
import com.main.aqarpaymentbackend.vendor.paymob.dto.PaymobCreateIntentionRequestDto;
import com.main.aqarpaymentbackend.vendor.paymob.dto.PaymobCreateIntentionResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "paymob-integration",
        url = "${paymob.base-url}"
)
public interface PaymobFeignClient {

    @PostMapping("/api/auth/tokens")
    PaymobAuthTokenResponseDto authToken(@RequestBody PaymobAuthTokenRequestDto request);

    @PostMapping("/v1/intention/")
    PaymobCreateIntentionResponseDto createIntention(
            @RequestHeader("Authorization") String token,
            @RequestBody PaymobCreateIntentionRequestDto request
    );
}
