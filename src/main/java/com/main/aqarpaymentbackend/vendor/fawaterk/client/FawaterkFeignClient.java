package com.main.aqarpaymentbackend.vendor.fawaterk.client;

import com.main.aqarpaymentbackend.vendor.fawaterk.dto.FawaterkOAuthRequestDto;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.FawaterkOAuthResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

@FeignClient(
        name = "fawaterk-integration",
        url = "https://app.fawaterk.com"
)
public interface FawaterkFeignClient {

    @PostMapping("/oauth/token")
    FawaterkOAuthResponseDto fawaterkOAuth(@RequestBody FawaterkOAuthRequestDto request);
}
