package com.main.aqarpaymentbackend.vendor.paymob.client;

import com.main.aqarpaymentbackend.vendor.paymob.dto.PaymobCreateIntentionRequestDto;
import com.main.aqarpaymentbackend.vendor.paymob.dto.PaymobCreateIntentionResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "paymob-integration",
        url = "${paymob.base-url}"
)
public interface PaymobFeignClient {

    @PostMapping("/v1/intention/")
    PaymobCreateIntentionResponseDto createIntention(
            @RequestHeader("Authorization") String authorization,
            @RequestBody PaymobCreateIntentionRequestDto request
    );


}
