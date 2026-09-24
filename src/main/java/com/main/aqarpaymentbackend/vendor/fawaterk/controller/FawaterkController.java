package com.main.aqarpaymentbackend.vendor.fawaterk.controller;

import com.main.aqarpaymentbackend.util.ReturnObject;
import com.main.aqarpaymentbackend.vendor.fawaterk.dto.FawaterkOAuthResponseDto;
import com.main.aqarpaymentbackend.vendor.fawaterk.service.FawaterkService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/fawaterk")
@RequiredArgsConstructor
@Slf4j
public class FawaterkController {

    private final FawaterkService fawaterkService;

    @PostMapping("/auth/token")
    public ResponseEntity<ReturnObject> fawaterkOAuth() {

        FawaterkOAuthResponseDto response = fawaterkService.fawaterkOAuth();

        return new ResponseEntity<>(new ReturnObject(
                "Authenticated Successfully",
                true,
                response
        ), HttpStatus.OK);
    }
}
