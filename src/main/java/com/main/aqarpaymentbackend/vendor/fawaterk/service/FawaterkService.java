package com.main.aqarpaymentbackend.vendor.fawaterk.service;

import com.main.aqarpaymentbackend.vendor.fawaterk.client.FawaterkFeignClient;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.FawaterkOAuthRequestDto;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.FawaterkOAuthResponseDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class FawaterkService {

    private final FawaterkFeignClient fawaterkFeignClient;

    @Value("${fawaterk.client-secret}")
    private String clientSecret;
    @Value("${fawaterk.client-id}")
    private String clientId;

    public FawaterkOAuthResponseDto fawaterkOAuth() {

        FawaterkOAuthRequestDto requestDto = FawaterkOAuthRequestDto.builder()
                .grantType("client_credentials")
                .clientSecret(clientSecret)
                .clientId(clientId)
                .build();
        log.info("Calling the external api");
        FawaterkOAuthResponseDto responseDto = fawaterkFeignClient.fawaterkOAuth(requestDto);
        log.info("the response from external api was: {}", requestDto);
        return responseDto;
    }
}
