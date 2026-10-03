package com.main.aqarpaymentbackend.vendor.stripe.client;

import com.main.aqarpaymentbackend.vendor.stripe.config.StripeFeignConfig;
import com.main.aqarpaymentbackend.vendor.stripe.dto.StripeSessionResponseDto;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;

import java.util.Map;

@FeignClient(
        name = "stripe-integration",
        url = "${stripe.base-url}",
        configuration = StripeFeignConfig.class
)
public interface StripeFeignClient {

    @PostMapping(
            value = "/v1/checkout/sessions",
            consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE
    )
    StripeSessionResponseDto createSession(@RequestBody Map<String, String> form);

    @GetMapping("/v1/checkout/sessions/{id}")
    StripeSessionResponseDto getSessionById(@PathVariable("id") String id);
}
