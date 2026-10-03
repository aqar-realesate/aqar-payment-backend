package com.main.aqarpaymentbackend.vendor.stripe.config;

import feign.auth.BasicAuthRequestInterceptor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;

public class StripeFeignConfig {

    @Bean
    public BasicAuthRequestInterceptor stripeBasicAuth(
            @Value("${stripe.secret-key}")
            String secretKey
    ) {
        return new BasicAuthRequestInterceptor(secretKey, "");
    }
}
